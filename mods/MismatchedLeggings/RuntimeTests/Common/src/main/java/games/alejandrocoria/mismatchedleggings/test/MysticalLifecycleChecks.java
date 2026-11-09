package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugments;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Paid native menus and actual disk persistence, shared by the two pinned distributions. */
public final class MysticalLifecycleChecks {
    private static final BlockPos CHEST = new BlockPos(4, 65, 0);

    private MysticalLifecycleChecks() {}

    public static void seed(MinecraftServer server) throws Exception {
        var player = RepairFixtures.player(server);
        for (String path : MysticalAugments.LEGGINGS) {
            var source = NativeAugments.item(path, "LUCK_I");
            var ordinary = new ItemStack(Items.LEATHER_LEGGINGS);
            var pair = MixedLeggingsFactory.createPair(source, ordinary);
            for (int first : List.of(2, 3)) {
                player.getInventory().clearContent();
                var menu = new WorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
                menu.getSlot(0).set(source.copy()); menu.getSlot(1).set(ordinary.copy());
                require(ItemStack.matches(menu.getSlot(2).getItem(), pair.primary())
                        && ItemStack.matches(menu.getSlot(3).getItem(), pair.inverse()), "Wrong Essence previews");
                menu.clicked(first, 0, ContainerInput.PICKUP, player);
                require(menu.isCommitted() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "Workbench did not pay once");
                require(ItemStack.matches(menu.getCarried(), first == 2 ? pair.primary() : pair.inverse()), "Wrong first extraction");
                menu.setCarried(ItemStack.EMPTY); menu.removed(player);
                var returned = player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
                require(returned.size() == 1 && ItemStack.matches(returned.getFirst(), first == 2 ? pair.inverse() : pair.primary()), "Close lost/duplicated paid partner");
            }
            player.getInventory().clearContent();
            var unpaid = new WorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
            unpaid.getSlot(0).set(source.copy()); unpaid.getSlot(1).set(ordinary.copy()); unpaid.removed(player);
            require(player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).count() == 2, "Unpaid close lost inputs");
            for (var root : List.of(pair.primary(), pair.inverse())) { workstations(server, player, source, root); }
        }
        var health = MixedLeggingsFactory.createPair(
                NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V", "STEP_ASSIST"),
                NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_I", "SPEED_III"));
        var armor = MixedLeggingsFactory.createPair(
                NativeAugments.item("awakened_supremium_leggings", "FIRE_RESISTANCE", "SLOWNESS_RESISTANCE"),
                NativeAugments.item("inferium_leggings", "LUCK_I"));
        server.overworld().setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        require(chest != null, "Saved chest missing"); chest.clearContent();
        var manifest = new JsonObject(); manifest.addProperty("pid", ProcessHandle.current().pid());
        var roots = List.of(health.primary(), health.inverse(), armor.primary(), armor.inverse());
        for (int slot = 0; slot < roots.size(); slot++) {
            var root = roots.get(slot); root.setDamageValue(3);
            chest.setItem(slot, root.copy());
            manifest.add("root" + slot, ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow());
        }
        chest.setChanged();
        write("saved.json", manifest.toString());
        write("lifecycle.txt", "Six Essence families/both orientations: paid workbench both extraction orders/close, unpaid return, paid donor anvil/book, enchanting pool, crafting/grindstone repair, paid trims and native Tinkering exclusion; four composed roots stored in actual chest\n");
    }

    private static void workstations(MinecraftServer server, ServerPlayer player, ItemStack source, ItemStack root) throws Exception {
        root.setDamageValue(root.getMaxDamage() / 2);
        var original = root.copy();
        var anvil = AnvilRepairChecks.ready(player, root.copy(), source.copy());
        int cost = anvil.getCost(); int levels = player.experienceLevel;
        var repaired = anvil.getSlot(2).safeTake(1, 1, player);
        require(!repaired.isEmpty() && repaired.getDamageValue() < root.getDamageValue() && cost > 0
                && player.experienceLevel == levels - cost && !anvil.getSlot(0).hasItem() && !anvil.getSlot(1).hasItem(), "Donor anvil did not repair/pay");
        history(root, repaired);
        var swift = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SWIFT_SNEAK);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); stored.set(swift, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        var enchanting = AnvilRepairChecks.ready(player, root.copy(), book);
        levels = player.experienceLevel; cost = enchanting.getCost();
        var enchanted = enchanting.getSlot(2).safeTake(1, 1, player);
        require(!enchanted.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(swift, enchanted) == 1
                && cost > 0 && player.experienceLevel == levels - cost && !enchanting.getSlot(1).hasItem(), "Paid native leggings book failed");
        history(root, enchanted);
        require(root.isEnchantable() && EnchantmentHelper.getAvailableEnchantmentResults(25, root,
                server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream())
                .stream().anyMatch(choice -> choice.enchantment().value().canEnchant(root)), "Native enchanting pool lost leggings");
        var craft = CraftingInput.of(2, 1, List.of(root.copy(), source.copy()));
        var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craft, server.overworld());
        require(recipe.isPresent(), "Essence donor crafting repair missing"); history(root, recipe.orElseThrow().value().assemble(craft));
        var grind = GrindstoneChecks.ready(player, enchanted.copy(), source.copy());
        var ground = grind.getSlot(2).safeTake(1, 1, player);
        require(!ground.isEmpty() && ground.getEnchantments().isEmpty() && !grind.getSlot(0).hasItem()
                && !grind.getSlot(1).hasItem(), "Grindstone did not consume/disenchant"); history(root, ground);
        var smith = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        smith.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
        smith.getSlot(1).set(root.copy()); smith.getSlot(2).set(new ItemStack(Items.DIAMOND, 2)); smith.createResult();
        var trimmed = smith.getSlot(3).safeTake(1, 1, player);
        require(!trimmed.isEmpty() && !smith.getSlot(1).hasItem() && smith.getSlot(0).getItem().getCount() == 1
                && smith.getSlot(2).getItem().getCount() == 1, "Trim did not pay once");
        var before = MixedLeggingsStructure.validData(root).orElseThrow();
        var after = MixedLeggingsStructure.validData(trimmed).orElseThrow();
        for (var entries : List.of(List.of(before.left().source(), after.left().source()), List.of(before.right().source(), after.right().source()))) {
            var changed = entries.get(1); require(changed.has(DataComponents.TRIM), "Source trim missing");
            changed.remove(DataComponents.TRIM); require(ItemStack.matches(entries.getFirst(), changed), "Trim changed source history/augments");
        }
        var handler = NativeAugments.invoke(NativeAugments.type("tileentity.TinkeringTableTileEntity"), "createInventoryHandler", new Class<?>[0]);
        var table = (AbstractContainerMenu) NativeAugments.type("container.TinkeringTableContainer")
                .getConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, handler.getClass(), BlockPos.class)
                .newInstance(1, player.getInventory(), handler, BlockPos.ZERO);
        require(table.getSlot(0).mayPlace(source) && !table.getSlot(0).mayPlace(root), "Tinkering admission/exclusion changed");
        require(ItemStack.matches(original, root), "Workstation mutated caller");
    }

    public static void reopened(ServerPlayer player) throws Exception {
        var server = player.level().getServer();
        var manifest = JsonParser.parseString(Files.readString(report("saved.json"))).getAsJsonObject();
        require(manifest.get("pid").getAsLong() != ProcessHandle.current().pid(), "Save was not reopened in a new process");
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        require(chest != null, "Reopened disk chest missing");
        for (int slot = 0; slot < 4; slot++) {
            var expected = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), manifest.get("root" + slot)).getOrThrow();
            var root = chest.getItem(slot).copy();
            require(ItemStack.matches(expected, root), "Reopen changed exact root/history " + slot);
            var ids = MysticalAugmentsCompatibility.selectedIds(root).stream().map(Object::toString).toList();
            var selected = slot < 2 ? List.of("mysticalagriculture:health_boost_v", "mysticalagriculture:speed_iii", "mysticalagriculture:step_assist")
                    : List.of("mysticalagriculture:fire_resistance", "mysticalagriculture:luck_i", "mysticalagriculture:slowness_resistance");
            require(ids.equals(selected), "Reopened effective union changed: " + ids);
            player.getInventory().clearContent(); player.removeAllEffects(); player.setItemSlot(EquipmentSlot.LEGS, root);
            if (slot >= 2) {
                player.setRemainingFireTicks(100); player.aiStep();
                require(player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isOnFire(), "Reopened worn armor effect failed");
            }
            var owned = MixedLeggingsStructure.validData(root).orElseThrow().left().source();
            Map<Item, Integer> purchases = new HashMap<>();
            for (Object augment : (List<?>) NativeAugments.invoke(NativeAugments.type("api.util.AugmentUtils"), "getAugments", new Class<?>[]{ItemStack.class}, owned)) {
                purchases.merge((Item) NativeAugments.invoke(augment, "getItem", new Class<?>[0]), 1, Integer::sum);
            }
            root.remove(DataComponents.UNBREAKABLE); root.setDamageValue(root.getMaxDamage() - 1);
            root.hurtAndBreak(2, player, EquipmentSlot.LEGS);
            require(root.isEmpty(), "Reopened actual break did not consume root");
            Map<Item, Integer> delivered = new HashMap<>();
            player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty())
                    .forEach(stack -> delivered.merge(stack.getItem(), stack.getCount(), Integer::sum));
            require(purchases.equals(delivered), "Reopened physical purchases were lost/duplicated " + slot);
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); player.getInventory().clearContent(); player.removeAllEffects();
        write("reopened.txt", "New JVM reopened actual world chest: four exact composed roots/full history, strongest effective unions, native Fire Resistance worn effect and real connected-player breaks with each left owner's exact purchases\n");
    }

    private static void history(ItemStack original, ItemStack result) {
        require(!result.isEmpty() && MixedLeggingsStructure.validData(result).isPresent()
                && original.get(LeggingsContent.mixedLeggingsDataType()).equals(result.get(LeggingsContent.mixedLeggingsDataType())), "Workstation changed full augment history");
    }

    private static Path report(String name) { return Path.of(System.getProperty("leggings.test.report"), name); }
    private static void write(String name, String value) throws Exception { Files.writeString(report(name), value); }
}
