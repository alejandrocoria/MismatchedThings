package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsData;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.core.BlockPos;

/** Focused actual-codec checks, with real registries instead of a fake successful decoder. */
final class MissingModsModelChecks {
    private MissingModsModelChecks() {}

    static void run(MinecraftServer server, ItemStack active) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag encoded = (CompoundTag) ItemStack.CODEC.encodeStart(ops, active).getOrThrow();
        var changed = encoded.copy();
        var patch = changed.getCompoundOrEmpty("components");
        patch.putLong("future:unavailable", 9_007_199_254_740_993L);
        ItemStack suspended = ItemStack.CODEC.parse(ops, changed).getOrThrow();
        var archive = archive(server, suspended);
        require(ModContent.getMixedBootsData(suspended).isEmpty()
                && suspended.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(), "Partial activation");
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
        require(conflicted.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(), "Competing gameplay value leaked");
        ItemStack again = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, conflicted).getOrThrow()).getOrThrow();
        require(ItemStack.matches(conflicted, again), "Conflict grows or disappears on reload");
        var available = conflict.original(); available.remove("future:unavailable");
        var readyButConflicted = collision.copy();
        readyButConflicted.getCompoundOrEmpty("components").remove("minecraft:attribute_modifiers");
        readyButConflicted.getCompoundOrEmpty("components").put(SuspendedBootsItemCodec.ARCHIVE,
                SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE,
                        SuspendedBootsData.of(available, conflict.projection(), conflict.conflicts())).getOrThrow());
        var blocked = ItemStack.CODEC.parse(ops, readyButConflicted).getOrThrow();
        require(archive(server, blocked).conflicted() && ModContent.getMixedBootsData(blocked).isEmpty(), "Available conflicting values silently chose a winner");

        var removal = collision.copy();
        removal.getCompoundOrEmpty("components").remove("minecraft:attribute_modifiers");
        removal.getCompoundOrEmpty("components").put("!minecraft:attribute_modifiers", new CompoundTag());
        var removalConflict = archive(server, ItemStack.CODEC.parse(ops, removal).getOrThrow());
        require(removalConflict.conflicts().contains("!minecraft:attribute_modifiers"), "Protected removal was ignored");
        var missingProjection = (CompoundTag) ItemStack.CODEC.encodeStart(ops, suspended).getOrThrow();
        missingProjection.getCompoundOrEmpty("components").remove("minecraft:equippable");
        var removedShell = archive(server, ItemStack.CODEC.parse(ops, missingProjection).getOrThrow());
        require(removedShell.conflicts().contains("!minecraft:equippable"), "Removal of protected equip projection was ignored");

        // Native integer decoding normalizes this float: preserve the original rather than accepting that loss.
        var lossy = encoded.copy();
        lossy.getCompoundOrEmpty("components").put("minecraft:damage", FloatTag.valueOf(3.5f));
        var lossyRoot = ItemStack.CODEC.parse(ops, lossy).getOrThrow();
        require(archive(server, lossyRoot).original().get("minecraft:damage").equals(FloatTag.valueOf(3.5f)), "Lossy decode accepted");
        require(ModContent.getMixedBootsData(lossyRoot).isEmpty(), "Lossy root reactivated");
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
        require(ModContent.getMixedBootsData(retained).isEmpty() && !retained.has(DataComponents.EQUIPPABLE), "Quarantine acquired active/equip semantics");
        CompoundTag container = new CompoundTag();
        container.putString("id", "minecraft:barrel");
        var items = new ListTag();
        var badSlot = badRoot.copy(); badSlot.putByte("Slot", (byte) 0); items.add(badSlot);
        var goodSlot = encoded.copy(); goodSlot.putByte("Slot", (byte) 1); items.add(goodSlot);
        container.put("Items", items);
        var loaded = BlockEntity.loadStatic(BlockPos.ZERO, Blocks.BARREL.defaultBlockState(), container, server.registryAccess());
        require(loaded instanceof BarrelBlockEntity, "Native outer loader discarded a whole container");
        var barrel = (BarrelBlockEntity) loaded;
        require(!barrel.getItem(0).isEmpty() && archive(server, barrel.getItem(0)).quarantined(), "Native container lost quarantined root");
        require(ItemStack.matches(active, barrel.getItem(1)), "Quarantine damaged an unrelated container slot");
    }

    private static SuspendedBootsData archive(MinecraftServer server, ItemStack root) {
        CompoundTag encoded = (CompoundTag) ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), root).getOrThrow();
        return SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, encoded.getCompoundOrEmpty("components").get(SuspendedBootsItemCodec.ARCHIVE)).getOrThrow();
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
