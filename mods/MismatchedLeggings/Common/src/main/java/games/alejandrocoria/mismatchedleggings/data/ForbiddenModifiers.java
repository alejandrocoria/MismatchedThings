package games.alejandrocoria.mismatchedleggings.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Live root authority; the native single-modifier field is only a projection. */
public record ForbiddenModifiers(boolean eternal, boolean soulbound) {
    public static final ForbiddenModifiers EMPTY = new ForbiddenModifiers(false, false);
    public static final Codec<ForbiddenModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("eternal").forGetter(ForbiddenModifiers::eternal),
            Codec.BOOL.fieldOf("soulbound").forGetter(ForbiddenModifiers::soulbound)
    ).apply(instance, ForbiddenModifiers::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForbiddenModifiers> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ForbiddenModifiers::eternal,
            ByteBufCodecs.BOOL, ForbiddenModifiers::soulbound,
            ForbiddenModifiers::new);

    public ForbiddenModifiers union(ForbiddenModifiers other) {
        return new ForbiddenModifiers(eternal || other.eternal, soulbound || other.soulbound);
    }

    public boolean isEmpty() {
        return !eternal && !soulbound;
    }
}
