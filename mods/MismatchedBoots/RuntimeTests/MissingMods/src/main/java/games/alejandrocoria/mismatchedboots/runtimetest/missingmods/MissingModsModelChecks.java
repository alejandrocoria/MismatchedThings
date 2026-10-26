package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsData;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/** Focused actual-codec checks, with real registries instead of a fake successful decoder. */
final class MissingModsModelChecks {
    private MissingModsModelChecks() {
    }

    static void run(MinecraftServer server, ItemStack active) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag encoded = (CompoundTag) ItemStack.CODEC.encodeStart(ops, active).getOrThrow();
        var changed = encoded.copy();
        var patch = changed.getCompoundOrEmpty("components");
        patch.putLong("future:unavailable", 9_007_199_254_740_993L);
        ItemStack suspended = ItemStack.CODEC.parse(ops, changed).getOrThrow();
        var archive = archive(server, suspended);
        require(ModContent.getMixedBootsData(suspended).isEmpty()
                        && suspended.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                "Partial activation");
        require(archive.original().getLongOr("future:unavailable", 0) == 9_007_199_254_740_993L, "Unknown value lost");

        // A conflicting native replacement is preserved next to the original, never selected or exposed.
        CompoundTag collision = (CompoundTag) ItemStack.CODEC.encodeStart(ops, suspended).getOrThrow();
        ListTag replacement = (ListTag) archive.original().get("minecraft:attribute_modifiers").copy();
        ((CompoundTag) replacement.get(0)).putDouble("amount", 73.25);
        collision.getCompoundOrEmpty("components").put("minecraft:attribute_modifiers", replacement);
        ItemStack conflicted = ItemStack.CODEC.parse(ops, collision).getOrThrow();
        var conflict = archive(server, conflicted);
        require(conflict.conflicted() && conflict.original().equals(archive.original()), "Conflict destroyed original");
        require(replacement.equals(conflict.conflicts().get("minecraft:attribute_modifiers")), "Competing value discarded");
        require(conflicted.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                "Competing gameplay value leaked");
        ItemStack again = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, conflicted).getOrThrow()).getOrThrow();
        require(ItemStack.matches(conflicted, again), "Conflict grows or disappears on reload");
        var available = conflict.original();
        available.remove("future:unavailable");
        var readyButConflicted = collision.copy();
        readyButConflicted.getCompoundOrEmpty("components").remove("minecraft:attribute_modifiers");
        readyButConflicted.getCompoundOrEmpty("components").put(SuspendedBootsItemCodec.ARCHIVE,
                                                                SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE,
                                                                                                     SuspendedBootsData.of(available,
                                                                                                                           conflict.projection(),
                                                                                                                           conflict.conflicts()))
                                                                                        .getOrThrow());
        var blocked = ItemStack.CODEC.parse(ops, readyButConflicted).getOrThrow();
        require(archive(server, blocked).conflicted() && ModContent.getMixedBootsData(blocked).isEmpty(),
                "Available conflicting values silently chose a winner");

        var removal = collision.copy();
        removal.getCompoundOrEmpty("components").remove("minecraft:attribute_modifiers");
        removal.getCompoundOrEmpty("components").put("!minecraft:attribute_modifiers", new CompoundTag());
        var removalConflict = archive(server, ItemStack.CODEC.parse(ops, removal).getOrThrow());
        require(removalConflict.conflicts().contains("!minecraft:attribute_modifiers"), "Protected removal was ignored");
        var missingProjection = (CompoundTag) ItemStack.CODEC.encodeStart(ops, suspended).getOrThrow();
        missingProjection.getCompoundOrEmpty("components").remove("minecraft:equippable");
        var removedShell = archive(server, ItemStack.CODEC.parse(ops, missingProjection).getOrThrow());
        require(removedShell.conflicts().contains("!minecraft:equippable"), "Removal of protected equip projection was ignored");

        nativeReadiness(server, encoded);
        jsonRoundTrips(server, active);
        var notBoots = changed.copy();
        notBoots.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").putString("id", "minecraft:stick");
        // Remove the original boot's component patch too: the readable source must really be a stick.
        notBoots.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").remove("components");
        require(!ItemStack.CODEC.parse(ops, notBoots).getOrThrow().has(DataComponents.EQUIPPABLE), "Known non-boots acquired equipability");
        var unequippable = changed.copy();
        unequippable.getCompoundOrEmpty("components").remove("minecraft:equippable");
        require(!ItemStack.CODEC.parse(ops, unequippable).getOrThrow().has(DataComponents.EQUIPPABLE), "Missing original equip slot was invented");
        var throwing = encoded.copy();
        throwing.getCompoundOrEmpty("components").putInt(MissingModsProof.MOD + ":throwing_property", 13);
        var throwingRoot = ItemStack.CODEC.parse(ops, throwing).getOrThrow();
        require(archive(server, throwingRoot).original().getIntOr(MissingModsProof.MOD + ":throwing_property", 0) == 13,
                "Throwing foreign codec lost its original value");
        var invalid = encoded.copy();
        invalid.getCompoundOrEmpty("components").putInt("minecraft:max_stack_size", 64);
        var invalidRoot = ItemStack.CODEC.parse(ops, invalid).getOrThrow();
        require(archive(server, invalidRoot).quarantined() && !invalidRoot.has(DataComponents.EQUIPPABLE), "Invalid core fields became usable");

        var malformed = new CompoundTag();
        var badRoot = encoded.copy();
        badRoot.getCompoundOrEmpty("components").putByteArray(SuspendedBootsItemCodec.ARCHIVE, new byte[]{10, 0});
        malformed.put("stack", badRoot);
        var retained = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), malformed)
                                    .read("stack", ItemStack.CODEC).orElseThrow();
        require(archive(server, retained).quarantined(), "Malformed archive was not kept opaque");
        require(ModContent.getMixedBootsData(retained).isEmpty() && !retained.has(DataComponents.EQUIPPABLE),
                "Quarantine acquired active/equip semantics");
        CompoundTag container = new CompoundTag();
        container.putString("id", "minecraft:barrel");
        var items = new ListTag();
        var badSlot = badRoot.copy();
        badSlot.putByte("Slot", (byte) 0);
        items.add(badSlot);
        var goodSlot = encoded.copy();
        goodSlot.putByte("Slot", (byte) 1);
        items.add(goodSlot);
        container.put("Items", items);
        var loaded = BlockEntity.loadStatic(BlockPos.ZERO, Blocks.BARREL.defaultBlockState(), container, server.registryAccess());
        require(loaded instanceof BarrelBlockEntity, "Native outer loader discarded a whole container");
        var barrel = (BarrelBlockEntity) loaded;
        require(!barrel.getItem(0).isEmpty() && archive(server, barrel.getItem(0)).quarantined(), "Native container lost quarantined root");
        require(ItemStack.matches(active, barrel.getItem(1)), "Quarantine damaged an unrelated container slot");
    }

    private static void nativeReadiness(MinecraftServer server, CompoundTag encoded) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var nativeInput = new CompoundTag();
        nativeInput.putString("id", "minecraft:diamond_boots");
        var nativePatch = new CompoundTag();
        nativePatch.put("minecraft:damage", FloatTag.valueOf(3.5f));
        nativeInput.put("components", nativePatch);
        int nativeDamage = ItemStack.CODEC.parse(ops, nativeInput).getOrThrow().getDamageValue();
        var normalized = encoded.copy();
        normalized.getCompoundOrEmpty("components").put("minecraft:damage", FloatTag.valueOf(3.5f));
        var normalizedRoot = ItemStack.CODEC.parse(ops, normalized).getOrThrow();
        require(ModContent.getMixedBootsData(normalizedRoot).isPresent() && normalizedRoot.getDamageValue() == nativeDamage,
                "Successful native normalization suspended an active root");

        String property = MissingModsProof.MOD + ":converting_property";
        for (boolean filtersUnknown : new boolean[]{false, true}) {
            var legacy = new CompoundTag();
            legacy.putInt("legacy", 42);
            if (filtersUnknown) {
                legacy.putString("unrecognized_reference", "future:removed");
            }

            var input = normalized.copy();
            var patch = input.getCompoundOrEmpty("components");
            patch.put(property, legacy.copy());
            var source = patch.getCompoundOrEmpty("mismatchedboots:mixed_boots_data").getCompoundOrEmpty("left");
            var sourcePatch = source.getCompoundOrEmpty("components");
            source.put("components", sourcePatch);
            sourcePatch.put(property, legacy.copy());
            nativePatch.put(property, legacy.copy());
            var nativeRoot = ItemStack.CODEC.parse(ops, nativeInput).getOrThrow();
            var nativeEncoded = (CompoundTag) ItemStack.CODEC.encodeStart(ops, nativeRoot).getOrThrow();
            var active = ItemStack.CODEC.parse(ops, input).getOrThrow();
            require(ModContent.getMixedBootsData(active).isPresent(), "Successful native conversion suspended an active root");

            // Model an unreadable old schema followed by a fully successful newer native reader.
            // This is a controlled codec transition, not evidence of an actual third-party upgrade.
            ItemStack suspended;
            MissingModsProof.legacyReadable = false;
            try {
                suspended = ItemStack.CODEC.parse(ops, input).getOrThrow();
            } finally {
                MissingModsProof.legacyReadable = true;
            }

            var retained = archive(server, suspended);
            require(legacy.equals(retained.original().get(property)), "Failed full read discarded original foreign data");
            require(legacy.equals(retained.original().getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                                          .getCompoundOrEmpty("left").getCompoundOrEmpty("components").get(property)),
                    "Failed full read discarded source data");
            require(suspended.getDamageValue() == nativeDamage && !retained.quarantined(), "Passive normalization quarantined a safe shell");
            var restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, suspended).getOrThrow()).getOrThrow();
            require(ModContent.getMixedBootsData(restored).isPresent() && ModContent.suspendedBootsData(restored).isEmpty(),
                    "Successful native conversion did not restore the whole root");
            var canonical = (CompoundTag) ItemStack.CODEC.encodeStart(ops, restored).getOrThrow();
            require(canonical.getCompoundOrEmpty("components").get(property)
                             .equals(nativeEncoded.getCompoundOrEmpty("components").get(property)), "Conversion differs from native item semantics");
            require(ItemStack.matches(active, restored), "Converted active and restored states differ");
            require(ItemStack.matches(restored, ItemStack.CODEC.parse(ops, canonical).getOrThrow()), "Converted root is unstable on reload");

            source.putString("id", "future:temporarily_missing_boots");
            int before = MissingModsProof.convertingReads;
            var missing = ItemStack.CODEC.parse(ops, input).getOrThrow();
            require(MissingModsProof.convertingReads == before, "Missing source reached foreign decoding before suspension");
            require(archive(server, missing).original().get(property).equals(legacy)
                            && ModContent.getMixedBootsData(missing).isEmpty(), "Missing source did not preserve the complete root");
        }
    }

    private static void jsonRoundTrips(MinecraftServer server, ItemStack active) {
        var nbt = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var json = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var root = active.copy();
        // Opaque vanilla custom NBT has its own JSON normalization; this probe isolates typed components.
        root.remove(DataComponents.CUSTOM_DATA);
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.UNBREAKING).orElseThrow(), 1);
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        var left = data.left().source();
        var right = data.right().source();
        for (boolean enabled : new boolean[]{false, true}) {
            var equipment = Equippable.builder(EquipmentSlot.FEET).setDamageOnHurt(enabled).build();
            left.set(DataComponents.EQUIPPABLE, equipment);
            right.set(DataComponents.EQUIPPABLE, equipment);
            root.set(DataComponents.EQUIPPABLE, equipment);
            root.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(left), new BootHalf(right)));
            var decoded = ItemStack.CODEC.parse(json, ItemStack.CODEC.encodeStart(json, root).getOrThrow()).getOrThrow();
            require(ItemStack.matches(root, decoded), "JSON changed active root/source boolean components");

            var encoded = (CompoundTag) ItemStack.CODEC.encodeStart(nbt, root).getOrThrow();
            var unavailable = encoded.copy();
            var opaque = new CompoundTag();
            opaque.putBoolean("enabled", enabled);
            opaque.putLongArray("values", new long[]{Long.MIN_VALUE, 9_007_199_254_740_993L, Long.MAX_VALUE});
            unavailable.getCompoundOrEmpty("components").put("future:unavailable", opaque);
            var suspended = ItemStack.CODEC.parse(nbt, unavailable).getOrThrow();
            var reloaded = ItemStack.CODEC.parse(json, ItemStack.CODEC.encodeStart(json, suspended).getOrThrow()).getOrThrow();
            require(ItemStack.matches(suspended, reloaded), "JSON changed the suspended archive or inert feet shell");

            var original = ModContent.suspendedBootsData(suspended).orElseThrow().original();
            original.remove("future:unavailable");
            suspended.set(MissingModsProof.<SuspendedBootsData>component(SuspendedBootsItemCodec.ARCHIVE),
                    SuspendedBootsData.of(original, SuspendedBootsData.equipmentProjection(), new CompoundTag()));
            var restored = ItemStack.CODEC.parse(json, ItemStack.CODEC.encodeStart(json, suspended).getOrThrow()).getOrThrow();
            require(ItemStack.matches(root, restored), "JSON restoration changed native root/source components");
        }
    }

    private static SuspendedBootsData archive(MinecraftServer server, ItemStack root) {
        CompoundTag encoded = (CompoundTag) ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), root)
                                                           .getOrThrow();
        return SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, encoded.getCompoundOrEmpty("components").get(SuspendedBootsItemCodec.ARCHIVE))
                                       .getOrThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
