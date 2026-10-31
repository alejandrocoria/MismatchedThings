package games.alejandrocoria.mismatchedleggings.network;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientReadyPayload() implements CustomPacketPayload {
    public static final Type<ClientReadyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "client_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientReadyPayload> STREAM_CODEC = StreamCodec.unit(new ClientReadyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
