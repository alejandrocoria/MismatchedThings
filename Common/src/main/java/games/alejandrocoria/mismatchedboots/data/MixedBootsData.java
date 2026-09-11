package games.alejandrocoria.mismatchedboots.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

public record MixedBootsData(BootHalf left, BootHalf right) {
    public static final Codec<MixedBootsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BootHalf.CODEC.fieldOf("left").forGetter(MixedBootsData::left),
                    BootHalf.CODEC.fieldOf("right").forGetter(MixedBootsData::right))
            .apply(instance, MixedBootsData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsData> STREAM_CODEC = StreamCodec.composite(
            BootHalf.STREAM_CODEC,
            MixedBootsData::left,
            BootHalf.STREAM_CODEC,
            MixedBootsData::right,
            MixedBootsData::new);

    public MixedBootsData {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }
}
