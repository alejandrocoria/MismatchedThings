package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Never transmitted: an independent required-channel negotiation control without either production mod. */
record NativeControlPayload() implements CustomPacketPayload {
    static final Type<NativeControlPayload> TYPE =
            new Type<>(Identifier.parse("mismatchedboots_protocol_test:native_control"));
    static final StreamCodec<FriendlyByteBuf, NativeControlPayload> CODEC = StreamCodec.unit(new NativeControlPayload());

    @Override
    public Type<NativeControlPayload> type() {
        return TYPE;
    }
}
