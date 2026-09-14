package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientReadyPayload() implements CustomPacketPayload {
    public static final ClientReadyPayload INSTANCE = new ClientReadyPayload();
    public static final Type<ClientReadyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "client_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientReadyPayload> STREAM_CODEC = StreamCodec.of(
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
