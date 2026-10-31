package games.alejandrocoria.mismatchedleggings.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.function.Supplier;

public record MixedLeggingsData(LeggingHalf left, LeggingHalf right) {
    private static final ThreadLocal<Boolean> PERSISTENT_ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> NETWORK_ACTIVE = new ThreadLocal<>();
    private static final Codec<MixedLeggingsData> UNGUARDED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LeggingHalf.CODEC.fieldOf("left").forGetter(MixedLeggingsData::left),
            LeggingHalf.CODEC.fieldOf("right").forGetter(MixedLeggingsData::right)
    ).apply(instance, MixedLeggingsData::new));
    public static final Codec<MixedLeggingsData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<MixedLeggingsData, T>> decode(DynamicOps<T> ops, T input) {
            return persistent(() -> UNGUARDED_CODEC.decode(ops, input));
        }

        @Override
        public <T> DataResult<T> encode(MixedLeggingsData value, DynamicOps<T> ops, T prefix) {
            return persistent(() -> UNGUARDED_CODEC.encode(value, ops, prefix));
        }
    };
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedLeggingsData> UNGUARDED_STREAM_CODEC = StreamCodec.composite(
            LeggingHalf.STREAM_CODEC, MixedLeggingsData::left,
            LeggingHalf.STREAM_CODEC, MixedLeggingsData::right, MixedLeggingsData::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, MixedLeggingsData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> network(() -> {
                UNGUARDED_STREAM_CODEC.encode(buffer, value);
                return null;
            }),
            buffer -> network(() -> UNGUARDED_STREAM_CODEC.decode(buffer)));

    public MixedLeggingsData {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    // Reject re-entry before a nested ItemStack can recursively decode another pair.
    private static <T> DataResult<T> persistent(Supplier<DataResult<T>> operation) {
        if (Boolean.TRUE.equals(PERSISTENT_ACTIVE.get())) {
            return DataResult.error(() -> "Mixed leggings data cannot be nested");
        }
        PERSISTENT_ACTIVE.set(true);
        try {
            return operation.get();
        } finally {
            PERSISTENT_ACTIVE.remove();
        }
    }

    private static <T> T network(Supplier<T> operation) {
        if (Boolean.TRUE.equals(NETWORK_ACTIVE.get())) {
            throw new IllegalArgumentException("Mixed leggings data cannot be nested");
        }
        NETWORK_ACTIVE.set(true);
        try {
            return operation.get();
        } finally {
            NETWORK_ACTIVE.remove();
        }
    }
}
