package games.alejandrocoria.mismatchedleggings.test;

import com.aetherteam.aetherii.inventory.menu.ArkeniumForgeMenu;
import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.aetherteam.aetherii.item.components.ReinforcementTier;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks.report;

/** Real paid menus and a saved-world fixture; no native Forge editing adapter or reload command. */
public final class AetherLifecycleChecks {
    private static final BlockPos CHEST = new BlockPos(4, 65, 0);

    private AetherLifecycleChecks() {}

    public static void verify(MinecraftServer server) throws Exception {
        var player = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "AetherLifecycle"));
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(100);
        player.connection.markClientLoaded();
        for (String family : AetherGameplayChecks.FAMILIES) {
            var source = family.equals("neptune") ? AetherGameplayChecks.charmed() : item(family + "_leggings");
            source.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST);
            var ordinary = new ItemStack(Items.IRON_LEGGINGS);
            var expected = MixedLeggingsFactory.createPair(source, ordinary);
            for (int first : List.of(2, 3)) {
                player.getInventory().clearContent();
                var menu = new WorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
                menu.getSlot(0).set(source.copy());
                menu.getSlot(1).set(ordinary.copy());
                require(ItemStack.matches(menu.getSlot(2).getItem(), expected.primary())
                        && ItemStack.matches(menu.getSlot(3).getItem(), expected.inverse()), "Wrong paid Aether previews");
                menu.clicked(first, 0, ContainerInput.PICKUP, player);
                require(menu.isCommitted() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "First take did not pay once");
                require(ItemStack.matches(menu.getCarried(), first == 2 ? expected.primary() : expected.inverse()), "First extraction changed root");
                menu.setCarried(ItemStack.EMPTY);
                menu.removed(player);
                var returned = player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
                require(returned.size() == 1 && ItemStack.matches(returned.getFirst(), first == 2 ? expected.inverse() : expected.primary()), "Close lost/duplicated paid partner");
            }
            player.getInventory().clearContent();
            var unpaid = new WorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
            unpaid.getSlot(0).set(source.copy());
            unpaid.getSlot(1).set(ordinary.copy());
            unpaid.removed(player);
            require(player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).count() == 2, "Unpaid close did not return inputs");
            for (var root : List.of(expected.primary(), expected.inverse())) {
                root.setDamageValue(root.getMaxDamage() / 2);
                var before = root.copy();
                var anvil = AnvilRepairChecks.ready(player, root.copy(), source.copy());
                var preview = anvil.getSlot(2).getItem().copy();
                int levels = player.experienceLevel;
                int cost = anvil.getCost();
                require(!preview.isEmpty() && preview.getDamageValue() < root.getDamageValue() && cost > 0, "Aether donor repair failed");
                var repaired = anvil.getSlot(2).safeTake(1, 1, player);
                require(ItemStack.matches(preview, repaired) && player.experienceLevel == levels - cost
                        && !anvil.getSlot(0).hasItem() && !anvil.getSlot(1).hasItem(), "Anvil did not pay/consume");
                history(root, repaired);
                var craft = CraftingInput.of(2, 1, List.of(root.copy(), source.copy()));
                var craftRecipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craft, server.overworld());
                var grind = GrindstoneChecks.ready(player, root.copy(), source.copy());
                require(craftRecipe.isPresent() == source.isCombineRepairable()
                        && !grind.getSlot(2).getItem().isEmpty() == source.isCombineRepairable(), "Native combine-repair admission differs");
                if (craftRecipe.isPresent()) {
                    history(root, craftRecipe.orElseThrow().value().assemble(craft));
                    history(root, grind.getSlot(2).safeTake(1, 1, player));
                }
                var smith = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
                smith.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
                smith.getSlot(1).set(root.copy());
                smith.getSlot(2).set(new ItemStack(Items.DIAMOND, 2));
                smith.createResult();
                var trimmed = smith.getSlot(3).safeTake(1, 1, player);
                require(!trimmed.isEmpty() && smith.getSlot(0).getItem().getCount() == 1
                        && smith.getSlot(2).getItem().getCount() == 1 && !smith.getSlot(1).hasItem(), "Trim did not pay once");
                var data = MixedLeggingsStructure.validData(trimmed).orElseThrow();
                require(data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM), "Trim missing from source");
                var nativeHalf = List.of(data.left().source(), data.right().source()).stream().filter(stack -> stack.is(source.getItem())).findFirst().orElseThrow();
                require(nativeHalf.get(AetherIIDataComponents.REINFORCEMENT_TIER) == ReinforcementTier.FIRST, "Smithing lost native tier");
                require(Objects.equals(nativeHalf.get(AetherIIDataComponents.CHARMS), source.get(AetherIIDataComponents.CHARMS)),
                        "Smithing changed original native charm holders");
                var forge = new ArkeniumForgeMenu(1, player.getInventory());
                forge.getSlot(0).set(root.copy());
                require(!forge.getSlot(3).mayPlace(item("charm_of_health_1")), "Mixed gained excluded native Forge installation");
                require(ItemStack.matches(before, root), "Menu changed caller's root");
            }
        }
        var source = AetherGameplayChecks.charmed();
        source.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.SECOND);
        var pair = MixedLeggingsFactory.createPair(source, item("burrukai_plate_leggings"));
        server.overworld().setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        require(chest != null, "Saved chest not created");
        chest.clearContent();
        int slot = 0;
        for (var root : List.of(pair.primary(), pair.inverse())) {
            chest.setItem(slot, root.copy());
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            Files.writeString(report("aether-saved-" + slot + ".json"), ItemStack.CODEC.encodeStart(ops, root).getOrThrow().toString());
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
            try {
                ItemStack.STREAM_CODEC.encode(bytes, root);
                require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(bytes)), "Registered native network-state roundtrip lost data");
            } finally { bytes.release(); }
            slot++;
        }
        chest.setChanged();
        server.saveEverything(true, true, true);
        Files.writeString(report("aether-lifecycle.txt"), "Six families: paid workbench both extraction orders/close, unpaid return, paid donor anvil, native crafting/grindstone admission, paid trims, Forge exclusion and immutable caller/history; saved two full native roots; registered stream roundtrip\n");
    }

    public static String verifySaved(MinecraftServer server) throws Exception {
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        require(chest != null, "Dedicated saved chest missing in reopened world");
        for (int slot = 0; slot < 2; slot++) {
            var expected = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString(Files.readString(report("aether-saved-" + slot + ".json")))).getOrThrow();
            require(ItemStack.matches(expected, chest.getItem(slot)), "Native root/charms/tiers changed across saved-world reopen");
        }
        return "Dedicated saved-world fixture reopened in integrated server: both roots and complete sources/charms/tiers retained (not dedicated-client transport)\n";
    }

    private static void history(ItemStack original, ItemStack result) {
        require(!result.isEmpty() && MixedLeggingsStructure.validData(result).isPresent()
                && original.get(LeggingsContent.mixedLeggingsDataType()).equals(result.get(LeggingsContent.mixedLeggingsDataType())),
                "Repair lost/changed full source history");
    }
}
