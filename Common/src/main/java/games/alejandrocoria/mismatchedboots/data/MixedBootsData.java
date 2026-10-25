package games.alejandrocoria.mismatchedboots.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.function.Supplier;

public record MixedBootsData(BootHalf left, BootHalf right) {
    private static final int MAX_CODEC_DEPTH = 1;
    private static final ThreadLocal<Integer> PERSISTENT_CODEC_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> NETWORK_CODEC_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Codec<MixedBootsData> UNGUARDED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                                                                                                       BootHalf.CODEC.fieldOf("left").forGetter(MixedBootsData::left),
                                                                                                                       BootHalf.CODEC.fieldOf("right").forGetter(MixedBootsData::right))
                                                                                                               .apply(instance, MixedBootsData::new));
    public static final Codec<MixedBootsData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<MixedBootsData, T>> decode(DynamicOps<T> ops, T input) {
            return withPersistentCodecDepth(() -> UNGUARDED_CODEC.decode(ops, input));
        }

        @Override
        public <T> DataResult<T> encode(MixedBootsData input, DynamicOps<T> ops, T prefix) {
            return withPersistentCodecDepth(() -> UNGUARDED_CODEC.encode(input, ops, prefix));
        }
    };
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsData> UNGUARDED_STREAM_CODEC = StreamCodec.composite(
            BootHalf.STREAM_CODEC,
            MixedBootsData::left,
            BootHalf.STREAM_CODEC,
            MixedBootsData::right,
            MixedBootsData::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> withNetworkCodecDepth(() -> {
                UNGUARDED_STREAM_CODEC.encode(buffer, value);
                return null;
            }),
            buffer -> withNetworkCodecDepth(() -> UNGUARDED_STREAM_CODEC.decode(buffer)));

    public MixedBootsData {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    private static <T> DataResult<T> withPersistentCodecDepth(Supplier<DataResult<T>> operation) {
        int depth = PERSISTENT_CODEC_DEPTH.get();
        if (depth >= MAX_CODEC_DEPTH) {
            return DataResult.error(() -> "Mixed boots data cannot be nested");
        }
        PERSISTENT_CODEC_DEPTH.set(depth + 1);
        try {
            return operation.get();
        } finally {
            restoreDepth(PERSISTENT_CODEC_DEPTH, depth);
        }
    }

    private static <T> T withNetworkCodecDepth(Supplier<T> operation) {
        int depth = NETWORK_CODEC_DEPTH.get();
        if (depth >= MAX_CODEC_DEPTH) {
            throw new IllegalArgumentException("Mixed boots data cannot be nested");
        }
        NETWORK_CODEC_DEPTH.set(depth + 1);
        try {
            return operation.get();
        } finally {
            restoreDepth(NETWORK_CODEC_DEPTH, depth);
        }
    }

    private static void restoreDepth(ThreadLocal<Integer> depthHolder, int depth) {
        if (depth == 0) {
            depthHolder.remove();
        } else {
            depthHolder.set(depth);
        }
    }
}
