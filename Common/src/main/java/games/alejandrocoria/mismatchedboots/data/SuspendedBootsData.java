package games.alejandrocoria.mismatchedboots.data;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

/** Typed NBT in a byte array avoids adding archive-envelope depth to the world's NBT tree. */
public final class SuspendedBootsData {
    public static final int MAX_BYTES = NbtAccounter.UNCOMPRESSED_NBT_QUOTA;
    // Three owned compound levels are independent of the original value's native depth allowance.
    public static final int MAX_DEPTH = Tag.MAX_DEPTH + 3;
    private static final Codec<SuspendedBootsData> BINARY_CODEC = Codec.BYTE_BUFFER.xmap(
            buffer -> {
                if (buffer.remaining() > MAX_BYTES) { throw new RecoveryRefusalException("Recovery payload exceeds byte budget"); }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.duplicate().get(bytes);
                return fromBytes(bytes);
            }, value -> ByteBuffer.wrap(value.bytes.clone()));
    public static final Codec<SuspendedBootsData> CODEC = new Codec<>() {
        @Override public <T> DataResult<Pair<SuspendedBootsData, T>> decode(DynamicOps<T> ops, T input) {
            Tag raw = input instanceof Tag tag ? tag : ops.convertTo(NbtOps.INSTANCE, input);
            validateBudget(raw, MAX_BYTES, MAX_DEPTH);
            try {
                var result = BINARY_CODEC.decode(ops, input);
                if (result.result().isPresent()) { return result; }
            } catch (RuntimeException failure) {
                // BlockEntity.loadStatic catches Throwable and drops the container: never throw for bounded bad archive bytes.
            }
            CompoundTag original = new CompoundTag();
            original.put(SuspendedBootsItemCodec.ARCHIVE, raw);
            return DataResult.success(Pair.of(create(original, new CompoundTag(), new CompoundTag(), true), ops.empty()));
        }
        @Override public <T> DataResult<T> encode(SuspendedBootsData value, DynamicOps<T> ops, T prefix) {
            return BINARY_CODEC.encode(value, ops, prefix);
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, SuspendedBootsData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.bytes.length); buffer.writeBytes(value.bytes); },
            buffer -> {
                int length = buffer.readVarInt();
                if (length < 0 || length > MAX_BYTES || length > buffer.readableBytes()) {
                    throw new RecoveryRefusalException("Invalid recovery network length");
                }
                byte[] bytes = new byte[length];
                buffer.readBytes(bytes);
                return CODEC.parse(NbtOps.INSTANCE, new ByteArrayTag(bytes)).getOrThrow();
            });

    private final byte[] bytes;
    private final CompoundTag data;

    private SuspendedBootsData(byte[] bytes, CompoundTag data) {
        this.bytes = bytes;
        this.data = data;
    }

    public static SuspendedBootsData of(CompoundTag original, CompoundTag projection, CompoundTag conflicts) {
        return create(original, projection, conflicts, false);
    }

    public static SuspendedBootsData quarantined(CompoundTag original, CompoundTag conflicts) {
        return create(original, new CompoundTag(), conflicts, true);
    }

    private static SuspendedBootsData create(CompoundTag original, CompoundTag projection, CompoundTag conflicts, boolean quarantined) {
        CompoundTag envelope = new CompoundTag();
        // Validate before any recursive copy, encoding or foreign component codec is invoked.
        envelope.put("original", original);
        envelope.put("projection", projection);
        envelope.put("conflicts", conflicts);
        envelope.putBoolean("quarantined", quarantined);
        validateBudget(envelope, MAX_BYTES, MAX_DEPTH);
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(envelope, new DataOutputStream(bytes));
            if (bytes.size() > MAX_BYTES) { throw new RecoveryRefusalException("Recovery payload exceeds byte budget"); }
            return fromBytes(bytes.toByteArray());
        } catch (IOException failure) { throw new RecoveryRefusalException("Cannot preserve recovery NBT", failure); }
    }

    public static SuspendedBootsData fromBytes(byte[] input) {
        if (input.length > MAX_BYTES) { throw new RecoveryRefusalException("Recovery payload exceeds byte budget"); }
        try {
            var stream = new ByteArrayInputStream(input);
            CompoundTag data = NbtIo.read(new DataInputStream(stream), new NbtAccounter(MAX_BYTES, MAX_DEPTH));
            if (stream.available() != 0 || !data.keySet().equals(Set.of("original", "projection", "conflicts", "quarantined"))
                    || !(data.get("original") instanceof CompoundTag)
                    || !(data.get("projection") instanceof CompoundTag)
                    || !(data.get("conflicts") instanceof CompoundTag)
                    || !(data.get("quarantined") instanceof ByteTag)) {
                throw new RecoveryRefusalException("Malformed recovery envelope");
            }
            validateBudget(data, MAX_BYTES, MAX_DEPTH);
            CompoundTag projection = data.getCompoundOrEmpty("projection");
            if (!projection.isEmpty() && !projection.equals(equipmentProjection())) {
                throw new RecoveryRefusalException("Unrecognized recovery projection");
            }
            for (String part : Set.of("original", "conflicts")) {
                CompoundTag fields = data.getCompoundOrEmpty(part);
                if (!data.getBooleanOr("quarantined", false)
                        && (fields.contains(SuspendedBootsItemCodec.ARCHIVE) || fields.contains("!" + SuspendedBootsItemCodec.ARCHIVE))) {
                    throw new RecoveryRefusalException("A recovery archive cannot contain another recovery archive");
                }
            }
            return new SuspendedBootsData(input.clone(), data);
        } catch (IOException | net.minecraft.nbt.NbtAccounterException failure) {
            throw new RecoveryRefusalException("Cannot read bounded recovery NBT", failure);
        }
    }

    public CompoundTag original() { return data.getCompoundOrEmpty("original").copy(); }
    public CompoundTag projection() { return data.getCompoundOrEmpty("projection").copy(); }
    public CompoundTag conflicts() { return data.getCompoundOrEmpty("conflicts").copy(); }
    public boolean conflicted() { return !data.getCompoundOrEmpty("conflicts").isEmpty(); }
    public boolean quarantined() { return data.getBooleanOr("quarantined", false); }
    public int encodedSize() { return bytes.length; }

    public static CompoundTag equipmentProjection() {
        var projection = new CompoundTag();
        var feet = new CompoundTag();
        feet.putString("slot", "feet");
        feet.putBoolean("damage_on_hurt", false);
        projection.put("minecraft:equippable", feet);
        return projection;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof SuspendedBootsData value && data.equals(value.data);
    }
    @Override public int hashCode() { return data.hashCode(); }

    /** Shared native heap accounting also bounds node/entry counts; arrays are counted in constant time. */
    public static void validateBudget(Tag value, long maximum, int depth) {
        measure(value, new long[]{maximum}, depth);
    }

    private static void measure(Tag value, long[] remaining, int depth) {
        if (depth < 0) { throw new RecoveryRefusalException("Recovery NBT exceeds depth budget"); }
        long own;
        if (value instanceof CompoundTag compound) {
            own = 48;
            spend(remaining, own);
            for (String key : compound.keySet()) {
                spend(remaining, 64L + 2L * key.length());
                measure(compound.get(key), remaining, depth - 1);
            }
        } else if (value instanceof ListTag list) {
            spend(remaining, 37L + 4L * list.size());
            for (Tag element : list) { measure(element, remaining, depth - 1); }
        } else {
            own = switch (value) {
                case ByteArrayTag array -> 24L + array.size();
                case IntArrayTag array -> 24L + 4L * array.size();
                case LongArrayTag array -> 24L + 8L * array.size();
                default -> value.sizeInBytes();
            };
            spend(remaining, own);
        }
    }

    private static void spend(long[] remaining, long amount) {
        if (amount < 0 || amount > remaining[0]) { throw new RecoveryRefusalException("Recovery NBT exceeds heap/entry budget"); }
        remaining[0] -= amount;
    }
}
