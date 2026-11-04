package games.alejandrocoria.mismatchedboots.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientReadyPayload() implements CustomPacketPayload {
    public static final ClientReadyPayload INSTANCE = new ClientReadyPayload();
    public static final Type<ClientReadyPayload> TYPE =
            new Type<>(CompositionRulesProtocol.READY_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientReadyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            },
            buffer -> {
                if (buffer.isReadable()) {
                    throw new DecoderException("Client-ready payload contains trailing data");
                }
                return INSTANCE;
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
