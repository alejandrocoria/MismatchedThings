package games.alejandrocoria.rulesspike;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record Ready() implements CustomPacketPayload {
    public static final Ready INSTANCE = new Ready();
    public static final Type<Ready> TYPE = new Type<>(Identifier.parse("mismatchedboots:ready_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<Ready> type() {
        return TYPE;
    }
}
