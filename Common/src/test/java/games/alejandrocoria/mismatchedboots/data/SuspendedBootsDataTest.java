package games.alejandrocoria.mismatchedboots.data;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspendedBootsDataTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void persistentAndNetworkCodecsRetainTypedOriginalsRemovalsAndConflicts() {
        CompoundTag original = original();
        CompoundTag conflicts = new CompoundTag();
        conflicts.putInt("future:energy", 99);
        var value = SuspendedBootsData.of(original, new CompoundTag(), conflicts);
        var encoded = SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        assertTrue(encoded instanceof ByteArrayTag);
        var persisted = SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, persisted.original());
        assertEquals(conflicts, persisted.conflicts());
        assertTrue(persisted.conflicted());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SuspendedBootsData.STREAM_CODEC.encode(buffer, persisted);
            assertEquals(value, SuspendedBootsData.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void neitherConstructorInputsNorExposedNbtAndEncodedBytesCanRewriteHistory() {
        CompoundTag original = original();
        var archive = SuspendedBootsData.of(original, new CompoundTag(), new CompoundTag());
        CompoundTag expected = original.copy();
        original.putInt("future:energy", 0);
        archive.original().putInt("future:energy", 1);
        ByteArrayTag output = (ByteArrayTag) SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, archive).getOrThrow();
        output.getAsByteArray()[0] = 0;
        assertEquals(expected, archive.original());
        assertEquals(archive, SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE,
                SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, archive).getOrThrow()).getOrThrow());
    }

    @Test void nativeHeapAndDepthBudgetsRejectBeforeCopyOrForeignDecoding() {
        var root = new CompoundTag();
        root.putByteArray("payload", new byte[128]);
        long exact = root.sizeInBytes();
        SuspendedBootsData.validateBudget(root, exact, 1);
        assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.validateBudget(root, exact - 1, 1));
        assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.validateBudget(root, exact, 0));
        var deep = new CompoundTag();
        var cursor = deep;
        for (int i = 0; i < SuspendedBootsData.MAX_DEPTH + 1; i++) {
            var child = new CompoundTag(); cursor.put("x", child); cursor = child;
        }
        assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.of(deep, new CompoundTag(), new CompoundTag()));
    }

    @Test void truncatedOrUnboundedNetworkLengthsFailBeforePayloadAllocation() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(SuspendedBootsData.MAX_BYTES + 1);
            assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.STREAM_CODEC.decode(buffer));
            buffer.clear(); buffer.writeVarInt(1024); buffer.writeByte(0);
            assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void malformedArchiveIsRetainedByNativeFieldLoadingInsteadOfReturningAPartialResult() {
        var field = new CompoundTag();
        field.putByteArray("archive", new byte[]{10, 0});
        var input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, field);
        var retained = input.read("archive", SuspendedBootsData.CODEC).orElseThrow();
        assertTrue(retained.quarantined());
        assertEquals(field.get("archive"), retained.original().get(SuspendedBootsItemCodec.ARCHIVE));
        field.putString("archive", "wrong component type");
        assertTrue(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, field)
                .read("archive", SuspendedBootsData.CODEC).orElseThrow().quarantined());
    }

    @Test void malformedAndRecursiveHistoryDoesNotAcquireAnEquipShell() {
        assertFalse(SuspendedBootsItemCodec.admittedSources(new CompoundTag()));
        var data = new CompoundTag();
        var source = new CompoundTag(); source.putString("id", "future:boots"); source.putInt("count", 1);
        data.put("left", source); data.put("right", source.copy());
        assertTrue(SuspendedBootsItemCodec.admittedSources(data));
        source.putString("id", "mismatchedboots:mixed_boots");
        assertFalse(SuspendedBootsItemCodec.admittedSources(data));
    }

    @Test void recoveryHistoryCannotBeEmbeddedInANormalSourceOrForgedProjection() {
        var source = new ItemStack(Items.IRON_BOOTS);
        source.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        new BootHalf(source);
        source.set(ModContent.createSuspendedBootsType(), SuspendedBootsData.of(original(), new CompoundTag(), new CompoundTag()));
        assertThrows(IllegalArgumentException.class, () -> new BootHalf(source));
        var fake = new CompoundTag(); fake.putInt("minecraft:damage", 0);
        assertThrows(RecoveryRefusalException.class, () -> SuspendedBootsData.of(original(), fake, new CompoundTag()));
    }

    @Test void nativeDepthRemainsInsideTheBinaryPayloadAndQuarantineDoesNotGrow() {
        var original = new CompoundTag();
        var cursor = original;
        for (int i = 0; i < net.minecraft.nbt.Tag.MAX_DEPTH - 1; i++) {
            var child = new CompoundTag(); cursor.put("x", child); cursor = child;
        }
        var deep = SuspendedBootsData.of(original, new CompoundTag(), new CompoundTag());
        assertEquals(deep, SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE,
                SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, deep).getOrThrow()).getOrThrow());
        var raw = new ByteArrayTag(new byte[]{10, 0});
        var quarantined = SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, raw).getOrThrow();
        var reloaded = SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE,
                SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, quarantined).getOrThrow()).getOrThrow();
        assertTrue(reloaded.quarantined());
        assertEquals(quarantined, reloaded);
        assertEquals(raw, reloaded.original().get(SuspendedBootsItemCodec.ARCHIVE));
    }

    private static CompoundTag original() {
        var original = new CompoundTag();
        original.putLongArray("future:longs", new long[]{Long.MIN_VALUE, 9_007_199_254_740_993L, Long.MAX_VALUE});
        original.putIntArray("future:ints", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        original.putByteArray("future:bytes", new byte[]{-128, -1, 0, 127});
        original.putInt("future:energy", 12347);
        original.put("!future:removed", new CompoundTag());
        return original;
    }
}
