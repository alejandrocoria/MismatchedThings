package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.RecoveryRefusalException;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsItemCodec;
import games.alejandrocoria.mismatchedleggings.test.mixin.ArmorWearInvoker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class RecoveryModelChecks {
    private RecoveryModelChecks() {
    }

    static void verify(MinecraftServer server, ItemStack active) {
        var registries = server.registryAccess();
        var raw = RecoveryChecks.encode(active, registries);
        var missing = raw.copy();
        missing.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedleggings:mixed_leggings_data")
                .getCompoundOrEmpty("left").putString("id", "absent:leggings");
        RecoveryFixture.walletReads = 0;
        var shell = RecoveryChecks.decode(missing, registries);
        require(RecoveryFixture.walletReads == 0, "Missing raw source invoked a foreign root/source codec");
        RecoveryChecks.inactive(shell);
        require(LeggingsContent.suspendedLeggingsData(shell).orElseThrow().original().getLongOr("leggings_skeleton_test:wallet", 0)
                        == 9_007_199_254_740_993L, "Archive rounded paid long resources");

        var conflict = RecoveryChecks.encode(shell, registries);
        conflict.getCompoundOrEmpty("components").put("minecraft:attribute_modifiers", new net.minecraft.nbt.ListTag());
        var conflicted = RecoveryChecks.decode(conflict, registries);
        require(LeggingsContent.suspendedLeggingsData(conflicted).orElseThrow().conflicted()
                        && conflicted.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                "Protected conflict was activated or discarded");
        require(ItemStack.matches(conflicted, RecoveryChecks.decode(RecoveryChecks.encode(conflicted, registries), registries)),
                "Conflict grew across a persistent reload");
        var removal = RecoveryChecks.encode(shell, registries);
        removal.getCompoundOrEmpty("components").remove("minecraft:equippable");
        require(LeggingsContent.suspendedLeggingsData(RecoveryChecks.decode(removal, registries)).orElseThrow().conflicts()
                        .contains("!minecraft:equippable"), "Removed shell projection was silently ignored");

        var broken = raw.copy();
        broken.getCompoundOrEmpty("components").put(SuspendedLeggingsItemCodec.ARCHIVE, new ByteArrayTag(new byte[]{1, 2, 3}));
        var opaque = RecoveryChecks.decode(broken, registries);
        require(LeggingsContent.suspendedLeggingsData(opaque).orElseThrow().quarantined()
                        && !MixedLeggingsStructure.canEquip(opaque), "Malformed archive acquired equipment permission");
        require(ItemStack.matches(opaque, RecoveryChecks.decode(RecoveryChecks.encode(opaque, registries), registries)),
                "Quarantined archive changed on repeated load");

        var wrongSource = missing.copy();
        wrongSource.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedleggings:mixed_leggings_data")
                .getCompoundOrEmpty("right").putString("id", "minecraft:stick");
        require(!MixedLeggingsStructure.canEquip(RecoveryChecks.decode(wrongSource, registries)), "Known non-leggings acquired history permission");

        // JSON has no typed NBT arrays: that native normalization is not an archive failure.
        // Keep typed-array exactness in the native NBT/lifecycle gate; check native equipment booleans here.
        var jsonSource = new ItemStack(Items.IRON_LEGGINGS);
        jsonSource.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).setDamageOnHurt(false).build());
        var jsonRoot = MixedLeggingsFactory.createPair(jsonSource, new ItemStack(Items.GOLDEN_LEGGINGS)).primary();
        var jsonOps = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = ItemStack.CODEC.encodeStart(jsonOps, jsonRoot).getOrThrow();
        require(ItemStack.matches(jsonRoot, ItemStack.CODEC.parse(jsonOps, json).getOrThrow()), "Native active JSON changed equipment components");
        var jsonMissing = RecoveryChecks.encode(jsonRoot, registries);
        jsonMissing.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedleggings:mixed_leggings_data")
                .getCompoundOrEmpty("left").putString("id", "absent:leggings");
        var jsonShell = RecoveryChecks.decode(jsonMissing, registries);
        var shellJson = ItemStack.CODEC.encodeStart(jsonOps, jsonShell).getOrThrow();
        require(ItemStack.matches(jsonShell, ItemStack.CODEC.parse(jsonOps, shellJson).getOrThrow()), "Opaque shell JSON changed archived data");

        try {
            SuspendedLeggingsData.validateBudget(new ByteArrayTag(new byte[100]), 16, Tag.MAX_DEPTH);
            throw new AssertionError("Archive ignored its native heap budget");
        } catch (RecoveryRefusalException expected) {
            // Safety refusal, not a silently truncated archive.
        }
    }

    static void workstations(MinecraftServer server, ItemStack shell) {
        var player = RepairFixtures.player(server);
        var original = RecoveryChecks.encode(shell, server.registryAccess());
        var rename = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        rename.getSlot(0).set(shell.copy());
        rename.setItemName("Safe suspended rename");
        var preview = rename.getSlot(2).getItem().copy();
        require(!preview.isEmpty() && preview.getHoverName().getString().equals("Safe suspended rename"),
                "Suspended shell cannot be renamed");
        var paid = rename.getSlot(2).safeTake(1, 1, player);
        require(ItemStack.matches(paid, RecoveryChecks.decode(RecoveryChecks.encode(preview, server.registryAccess()), server.registryAccess()))
                        && !LeggingsContent.suspendedLeggingsData(paid).orElseThrow().conflicted(), "Native rename invented a recovery conflict");

        for (var addition : List.of(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_INGOT),
                AnvilRepairChecks.book(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 1))) {
            var anvil = new AnvilMenu(2, player.getInventory(), ContainerLevelAccess.NULL);
            anvil.getSlot(0).set(shell.copy());
            anvil.getSlot(1).set(addition);
            require(!anvil.getSlot(2).hasItem(), "Suspended repair/book operation was admitted");
        }
        var grindstone = new GrindstoneMenu(3, player.getInventory(), ContainerLevelAccess.NULL);
        grindstone.getSlot(0).set(shell.copy());
        require(!grindstone.getSlot(2).hasItem(), "Suspended grindstone mutation was admitted");
        var smithing = new SmithingMenu(4, player.getInventory(), ContainerLevelAccess.create(server.overworld(), new net.minecraft.core.BlockPos(0, 65, 0)));
        smithing.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        smithing.getSlot(1).set(shell.copy());
        smithing.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
        require(!smithing.getSlot(3).hasItem(), "Suspended upgrade was admitted");
        var crafting = new CraftingMenu(5, player.getInventory(), ContainerLevelAccess.create(server.overworld(), player.blockPosition()));
        crafting.getInputGridSlots().get(0).set(shell.copy());
        crafting.getInputGridSlots().get(1).set(new ItemStack(Items.IRON_LEGGINGS));
        require(!crafting.getSlot(0).hasItem(), "Suspended crafting donor repair was admitted");
        try {
            MixedLeggingsRecomposer.recompose(shell, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS));
            throw new AssertionError("Suspended root was recomposed from historical sources");
        } catch (IllegalArgumentException expected) {
            // Recovery restores the paid root through codecs, never through recomposition.
        }
        require(!shell.isEnchantable() && MixedLeggingsStructure.validData(shell).isEmpty()
                        && original.equals(RecoveryChecks.encode(shell, server.registryAccess())), "Inactive checks mutated parked data");
    }

    static void inactiveWear(MinecraftServer server, ServerPlayer player) {
        var shell = player.getItemBySlot(EquipmentSlot.LEGS);
        var raw = RecoveryChecks.encode(shell, server.registryAccess());
        require(player.getArmorValue() == 0, "Inactive server shell still provides armor");
        ((ArmorWearInvoker) player).leggings$hurtArmor(player.damageSources().generic(), 8);
        player.takeXpDelay = 0;
        int experience = player.totalExperience;
        new ExperienceOrb(server.overworld(), 0, 65, 0, 5).playerTouch(player);
        require(raw.equals(RecoveryChecks.encode(shell, server.registryAccess())) && player.totalExperience > experience,
                "Combat wear/Mending touched the parked root");
    }
}
