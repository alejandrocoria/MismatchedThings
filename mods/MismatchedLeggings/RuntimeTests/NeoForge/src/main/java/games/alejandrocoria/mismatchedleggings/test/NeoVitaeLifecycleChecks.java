package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.NeoVitaeChecks.NativeApi;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real paid native operations followed by a second JVM reading the saved barrel, not a codec-only reload. */
final class NeoVitaeLifecycleChecks {
    private static final BlockPos BARREL = new BlockPos(0, 80, 0);

    private NeoVitaeLifecycleChecks() {}

    static void save(MinecraftServer server) throws Exception {
        var api = new NativeApi();
        var player = player(server);
        var diamond = infuse(server, api, new ItemStack(Items.DIAMOND_LEGGINGS), "spiritus_gem_petty");
        diamond = bloodMending(server, api, diamond);
        var iron = infuse(server, api, new ItemStack(Items.IRON_LEGGINGS), "spiritus_gem_common");
        require(api.fill(diamond, api.raw, 40, true) == 40 && api.fill(iron, api.raw, 100, true) == 100, "Native source fill failed");
        var compatible = workbench(player, diamond, iron, false);
        for (var root : compatible) {
            tuple(api, root, 1024, 70, api.raw);
            require(Boolean.TRUE.equals(root.get(api.blood)), "Paid creation lost Blood Mending");
        }
        for (var root : workbench(player, diamond, new ItemStack(Items.IRON_LEGGINGS), true)) {
            tuple(api, root, 64, 20, api.raw);
            require(root.has(api.blood), "One-sided paid creation lost its marker");
        }

        var live = compatible.getFirst();
        var beforeTransfer = live.copy();
        var history = MixedLeggingsStructure.validData(live).orElseThrow();
        require(api.drain(live, api.raw, 70, false) == 70 && ItemStack.matches(beforeTransfer, live), "Simulated drain mutated root");
        require(api.drain(live, api.raw, 1000, true) == 70 && api.fill(live, api.ruina, 12.5, true) == 12.5,
                "Native depletion/retyping failed");
        require(history.equals(MixedLeggingsStructure.validData(live).orElseThrow()), "Transfer changed source history");
        var trimmed = smithing(server, player, live, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.AMETHYST_SHARD);
        same(api, live, trimmed);
        var trim = MixedLeggingsStructure.validData(trimmed).orElseThrow();
        require(trim.left().source().has(DataComponents.TRIM) && trim.right().source().has(DataComponents.TRIM), "Trim missed a source");
        var upgraded = smithing(server, player, trimmed, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.NETHERITE_INGOT);
        same(api, trimmed, upgraded);
        var tier = MixedLeggingsStructure.validData(upgraded).orElseThrow();
        require(tier.left().source().is(Items.NETHERITE_LEGGINGS) && tier.right().source().is(Items.IRON_LEGGINGS)
                        && Double.valueOf(40).equals(tier.left().source().get(api.amount))
                        && Double.valueOf(100).equals(tier.right().source().get(api.amount)), "Tier upgrade changed original balances");
        require(api.drain(compatible.get(1), api.raw, 1000, true) == 70, "Could not deplete the empty control");

        var ruina = iron.copy();
        require(api.drain(ruina, api.raw, 100, true) == 100 && api.fill(ruina, api.ruina, 100, true) == 100, "Native conflict setup failed");
        var conflicting = workbench(player, diamond, ruina, true);
        tuple(api, conflicting.getFirst(), 1024, 40, api.raw);
        tuple(api, conflicting.get(1), 1024, 100, api.ruina);
        var beforeRejectedFill = conflicting.getFirst().copy();
        require(api.fill(conflicting.getFirst(), api.ruina, 20, true) == 0
                        && ItemStack.matches(beforeRejectedFill, conflicting.getFirst()), "Incompatible fill converted charge");
        require(api.drain(conflicting.getFirst(), api.raw, 7.25, true) == 7.25, "Fractional native drain failed");

        var plain = workbench(player, new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS), false);
        var later = infuse(server, api, plain.getFirst(), "spiritus_gem_greater");
        later = bloodMending(server, api, later);
        require(api.fill(later, api.ruina, 13.125, true) == 13.125, "Later root-only native fill failed");
        var laterTier = smithing(server, player, later, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.NETHERITE_INGOT);
        same(api, later, laterTier);
        tuple(api, laterTier, 4096, 13.125, api.ruina);
        require(!plain.get(1).has(api.capacity) && !plain.get(1).has(api.blood), "Plain inverse acquired an installation");
        rejected(player, api, diamond, new ItemStack(Items.IRON_LEGGINGS));
        var roots = new ArrayList<>(List.of(upgraded, compatible.get(1), conflicting.getFirst(), conflicting.get(1), laterTier, plain.get(1)));
        roots.addAll(repairs(server, player, api, upgraded));

        server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "Missing persistence barrel");
        var saved = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < roots.size(); index++) {
            barrel.setItem(index, roots.get(index));
            saved.add(ItemStack.CODEC.encodeStart(ops, roots.get(index)).getOrThrow());
        }
        barrel.setChanged();
        var manifest = new JsonObject();
        manifest.addProperty("process", ProcessHandle.current().pid());
        manifest.add("roots", saved);
        Files.writeString(report("neovitae-saved.json"), manifest.toString());
        Files.writeString(report("neovitae-lifecycle-server.txt"),
                "4 paid Workbench transactions, both extraction orders, full history and single payment; malformed no-payment control\n"
                        + "Native forge infusion/Blood Mending ingredient/fuel debits, fill/drain/simulation/type refusal; 3 paid smithing takes\n"
                        + "Paid anvil, crafting repair and grindstone: exact live infusion/marker/history, no refill; 9 complete roots stored in world barrel\n");
    }

    static void restart(MinecraftServer server) throws Exception {
        var api = new NativeApi();
        var manifest = JsonParser.parseString(Files.readString(report("neovitae-saved.json"))).getAsJsonObject();
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "World reopen did not use another JVM");
        server.overworld().getChunk(0, 0);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "World reopen lost persistence barrel");
        var saved = manifest.getAsJsonArray("roots");
        require(saved.size() == 9, "Incomplete saved expectations");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < saved.size(); index++) {
            var expected = ItemStack.CODEC.parse(ops, saved.get(index)).getOrThrow();
            require(ItemStack.matches(expected, barrel.getItem(index)), "Reopen changed complete root/history " + index);
        }
        var live = barrel.removeItemNoUpdate(0);
        var history = MixedLeggingsStructure.validData(live).orElseThrow();
        require(api.drain(live, api.ruina, .125, true) == .125
                        && history.equals(MixedLeggingsStructure.validData(live).orElseThrow()), "Post-reopen drain changed history");
        var trimmed = smithing(server, player(server), live, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.GOLD_INGOT);
        same(api, live, trimmed);
        barrel.setItem(0, trimmed);
        var empty = barrel.getItem(1);
        require(Double.valueOf(0).equals(empty.get(api.amount)) && api.fill(empty, api.ruina, .5, true) == .5,
                "Reopened empty container cannot change type");
        var absent = barrel.removeItemNoUpdate(5);
        require(!absent.has(api.capacity) && !absent.has(api.amount) && !absent.has(api.type) && !absent.has(api.blood),
                "Reopen resurrected an absent installation");
        var installed = infuse(server, api, absent, "spiritus_gem_petty");
        tuple(api, installed, 64, 0, api.raw);
        barrel.setItem(5, installed);
        barrel.setChanged();
        Files.writeString(report("neovitae-restart-server.txt"),
                "Different JVM reopened 9 full native roots/history from the same world; drain, paid trim, empty retype and later infusion passed\n");
    }

    private static List<ItemStack> workbench(ServerPlayer player, ItemStack first, ItemStack second, boolean inverseFirst) {
        var beforeFirst = first.copy();
        var beforeSecond = second.copy();
        var menu = new WorkbenchMenu(1, player.getInventory());
        menu.getSlot(0).set(first.copy());
        menu.getSlot(1).set(second.copy());
        var primary = menu.getSlot(2).getItem().copy();
        var inverse = menu.getSlot(3).getItem().copy();
        require(!primary.isEmpty() && !inverse.isEmpty() && !menu.isCommitted(), "Missing unpaid previews");
        int firstSlot = inverseFirst ? 3 : 2;
        int secondSlot = inverseFirst ? 2 : 3;
        var firstTaken = menu.getSlot(firstSlot).safeTake(1, 1, player);
        require(menu.isCommitted() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem()
                        && ItemStack.matches(inverseFirst ? primary : inverse, menu.getSlot(secondSlot).getItem()), "Workbench did not pay once");
        var secondTaken = menu.getSlot(secondSlot).safeTake(1, 1, player);
        var results = inverseFirst ? List.of(secondTaken, firstTaken) : List.of(firstTaken, secondTaken);
        require(ItemStack.matches(primary, results.getFirst()) && ItemStack.matches(inverse, results.get(1))
                        && menu.getSlot(2).safeTake(1, 1, player).isEmpty() && menu.getSlot(3).safeTake(1, 1, player).isEmpty(),
                "Workbench changed paid outputs or allowed duplicate takes");
        for (int index = 0; index < 2; index++) {
            var history = MixedLeggingsStructure.validData(results.get(index)).orElseThrow();
            require(ItemStack.matches(history.left().source(), index == 0 ? beforeFirst : beforeSecond)
                            && ItemStack.matches(history.right().source(), index == 0 ? beforeSecond : beforeFirst), "Paid source history changed");
        }
        require(ItemStack.matches(first, beforeFirst) && ItemStack.matches(second, beforeSecond), "Workbench mutated external sources");
        return results;
    }

    private static void rejected(ServerPlayer player, NativeApi api, ItemStack first, ItemStack second) {
        var invalid = first.copy();
        invalid.set(api.amount, invalid.get(api.capacity) + 1);
        var before = invalid.copy();
        var other = second.copy();
        var menu = new WorkbenchMenu(1, player.getInventory());
        menu.getSlot(0).set(invalid);
        menu.getSlot(1).set(other);
        require(!menu.getSlot(2).hasItem() && !menu.getSlot(3).hasItem()
                        && menu.getSlot(2).safeTake(1, 1, player).isEmpty() && menu.getSlot(3).safeTake(1, 1, player).isEmpty()
                        && !menu.isCommitted() && ItemStack.matches(before, menu.getSlot(0).getItem())
                        && ItemStack.matches(second, menu.getSlot(1).getItem()), "Rejected workbench preview consumed payment");
    }

    private static ItemStack smithing(MinecraftServer server, ServerPlayer player, ItemStack root, Item template, Item addition) {
        var before = root.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(template));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root.copy());
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(addition));
        var preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!preview.isEmpty() && ItemStack.matches(preview, menu.getCarried()) && ItemStack.matches(before, root)
                        && !menu.getSlot(SmithingMenu.BASE_SLOT).hasItem() && !menu.getSlot(SmithingMenu.TEMPLATE_SLOT).hasItem()
                        && !menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).hasItem(), "Paid smithing take failed");
        var result = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        return result;
    }

    private static List<ItemStack> repairs(MinecraftServer server, ServerPlayer player, NativeApi api, ItemStack live) {
        var damaged = live.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 20);
        var history = MixedLeggingsStructure.validData(damaged).orElseThrow();
        player.giveExperienceLevels(100);
        var anvil = AnvilRepairChecks.ready(player, damaged.copy(), new ItemStack(Items.IRON_INGOT, 8));
        int levels = player.experienceLevel;
        int cost = anvil.getCost();
        var repaired = anvil.getSlot(2).safeTake(1, 1, player);
        require(!repaired.isEmpty() && repaired.getDamageValue() < damaged.getDamageValue() && cost > 0
                        && player.experienceLevel == levels - cost && !anvil.getSlot(0).hasItem()
                        && anvil.getSlot(1).getItem().getCount() < 8 && anvil.getSlot(2).safeTake(1, 1, player).isEmpty(),
                "Anvil failed to pay/repair exactly once");

        var donor = new ItemStack(Items.IRON_LEGGINGS);
        donor.setDamageValue(donor.getMaxDamage() - 10);
        var crafting = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        crafting.getInputGridSlots().get(0).set(damaged.copy());
        crafting.getInputGridSlots().get(8).set(donor.copy());
        var crafted = crafting.getSlot(0).safeTake(1, 1, player);
        require(!crafted.isEmpty() && crafted.getDamageValue() < damaged.getDamageValue()
                        && crafting.getInputGridSlots().stream().noneMatch(slot -> slot.hasItem())
                        && crafting.getSlot(0).safeTake(1, 1, player).isEmpty(), "Crafting repair failed payment or duplicated output");

        var enchanted = damaged.copy();
        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        enchanted.enchant(protection, 1);
        var grindstone = GrindstoneChecks.ready(player, enchanted, donor.copy());
        var ground = grindstone.getSlot(2).safeTake(1, 1, player);
        require(!ground.isEmpty() && ground.getDamageValue() < damaged.getDamageValue() && ground.getEnchantments().getLevel(protection) == 0
                        && !grindstone.getSlot(0).hasItem() && !grindstone.getSlot(1).hasItem()
                        && grindstone.getSlot(2).safeTake(1, 1, player).isEmpty(), "Grindstone repair failed payment/disenchantment");
        for (var output : List.of(repaired, crafted, ground)) {
            same(api, live, output);
            require(history.equals(MixedLeggingsStructure.validData(output).orElseThrow()), "Repair changed source history");
        }
        return List.of(repaired, crafted, ground);
    }

    private static ItemStack infuse(MinecraftServer server, NativeApi api, ItemStack target, String gem) throws Exception {
        return api.forge(server, List.of(target, NeoVitaeChecks.item(gem)), 0);
    }

    private static ItemStack bloodMending(MinecraftServer server, NativeApi api, ItemStack target) throws Exception {
        return api.forge(server, List.of(target, NeoVitaeChecks.item("tabula_robur"),
                new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.NETHER_WART)), 200);
    }

    private static void tuple(NativeApi api, ItemStack root, double capacity, double amount, Object type) {
        require(Double.valueOf(capacity).equals(root.get(api.capacity)) && Double.valueOf(amount).equals(root.get(api.amount))
                        && type.equals(root.get(api.type)), "Unexpected native tuple after paid operation");
    }

    private static void same(NativeApi api, ItemStack previous, ItemStack output) {
        require(Objects.equals(previous.get(api.capacity), output.get(api.capacity))
                        && Objects.equals(previous.get(api.amount), output.get(api.amount))
                        && Objects.equals(previous.get(api.type), output.get(api.type))
                        && Objects.equals(previous.get(api.blood), output.get(api.blood)), "Paid operation replaced live infusion state");
    }

    private static ServerPlayer player(MinecraftServer server) {
        // Native research listeners send packets; this offline fixture player has a no-op connection.
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
