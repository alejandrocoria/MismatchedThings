package games.alejandrocoria.mismatchedleggings.data;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspendedLeggingsDataTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.IRON_LEGGINGS.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build()).build());
    }

    @Test
    void archivePreservesTypedStateAndDefendsAgainstMutationThroughBothCodecs() {
        var raw = new CompoundTag();
        raw.putLongArray("future:wallet", new long[]{Long.MIN_VALUE, 9_007_199_254_740_993L, Long.MAX_VALUE});
        raw.put("!future:removed", new CompoundTag());
        var conflicts = new CompoundTag();
        conflicts.putInt("future:wallet", 1);
        var archive = SuspendedLeggingsData.of(raw, SuspendedLeggingsData.equipmentProjection(), conflicts);
        var expected = raw.copy();
        raw.putInt("future:wallet", 0);
        archive.original().putInt("future:wallet", 2);
        assertEquals(expected, archive.original());
        assertTrue(archive.conflicted());
        var encoded = SuspendedLeggingsData.CODEC.encodeStart(NbtOps.INSTANCE, archive).getOrThrow();
        assertEquals(archive, SuspendedLeggingsData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        ((ByteArrayTag) encoded).getAsByteArray()[0] = 0;
        assertEquals(expected, archive.original());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SuspendedLeggingsData.STREAM_CODEC.encode(buffer, archive);
            assertEquals(archive, SuspendedLeggingsData.STREAM_CODEC.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(SuspendedLeggingsData.MAX_BYTES + 1);
            assertThrows(RecoveryRefusalException.class, () -> SuspendedLeggingsData.STREAM_CODEC.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(1024);
            buffer.writeByte(0);
            assertThrows(RecoveryRefusalException.class, () -> SuspendedLeggingsData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void budgetsRefuseBeforeCopyAndMalformedArchiveStaysOpaqueWithoutGrowth() {
        var raw = new CompoundTag();
        raw.putByteArray("payload", new byte[128]);
        SuspendedLeggingsData.validateBudget(raw, raw.sizeInBytes(), 1);
        assertThrows(RecoveryRefusalException.class, () -> SuspendedLeggingsData.validateBudget(raw, raw.sizeInBytes() - 1, 1));
        assertThrows(RecoveryRefusalException.class, () -> SuspendedLeggingsData.validateBudget(raw, raw.sizeInBytes(), 0));
        var bytes = new ByteArrayTag(new byte[]{10, 0});
        var quarantined = SuspendedLeggingsData.CODEC.parse(NbtOps.INSTANCE, bytes).getOrThrow();
        assertTrue(quarantined.quarantined());
        assertTrue(quarantined.projection().isEmpty());
        assertEquals(bytes, quarantined.original().get(SuspendedLeggingsItemCodec.ARCHIVE));
        assertEquals(quarantined, SuspendedLeggingsData.CODEC.parse(NbtOps.INSTANCE,
                SuspendedLeggingsData.CODEC.encodeStart(NbtOps.INSTANCE, quarantined).getOrThrow()).getOrThrow());
        assertThrows(RecoveryRefusalException.class,
                () -> SuspendedLeggingsData.of(raw, raw, new CompoundTag()));
    }

    @Test
    void nestedHistoryAndForgedSourcesCannotAcquireAdmission() {
        var source = new ItemStack(Items.IRON_LEGGINGS);
        source.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build());
        new LeggingHalf(source);
        source.set(LeggingsContent.createSuspendedDataType(), SuspendedLeggingsData.of(
                new CompoundTag(), new CompoundTag(), new CompoundTag()));
        assertThrows(IllegalArgumentException.class, () -> new LeggingHalf(source));
        assertFalse(SuspendedLeggingsItemCodec.admittedSources(new CompoundTag()));
        var encoded = new CompoundTag();
        var side = new CompoundTag();
        side.putString("id", "future:leggings");
        encoded.put("left", side);
        encoded.put("right", side.copy());
        assertTrue(SuspendedLeggingsItemCodec.admittedSources(encoded));
        side.putString("id", "mismatchedleggings:mixed_leggings");
        assertFalse(SuspendedLeggingsItemCodec.admittedSources(encoded));
    }
}
