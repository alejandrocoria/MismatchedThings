package games.alejandrocoria.mismatchedboots.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

/** Revision-2 snapshot. Registration and client publication belong to the loader adapters/lifecycle. */
public record CompositionRulesPayload(CompositionRules rules) implements CustomPacketPayload {
    static final int MAX_PAYLOAD_BYTES = 512 * 1_024;

    public static final Type<CompositionRulesPayload> TYPE = new Type<>(CompositionRulesProtocol.RULES_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CompositionRulesPayload> STREAM_CODEC =
            StreamCodec.of(CompositionRulesPayload::encode, CompositionRulesPayload::decode);

    public CompositionRulesPayload {
        Objects.requireNonNull(rules, "rules");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CompositionRulesPayload payload) {
        // Bound work/storage before touching the outgoing packet, including rejected large local configurations.
        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(
                Unpooled.buffer(256, MAX_PAYLOAD_BYTES), buffer.registryAccess());
        try {
            try {
                AttributeRulesSectionCodec.encode(encoded, payload.rules.attributes());
                ComponentRulesSectionCodec.encode(encoded, payload.rules.components());
            } catch (IndexOutOfBoundsException exception) {
                throw new EncoderException("Composition-rules payload exceeds " + MAX_PAYLOAD_BYTES + " bytes", exception);
            }

            buffer.writeBytes(encoded);
        } finally {
            encoded.release();
        }
    }

    private static CompositionRulesPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
            throw new DecoderException("Composition-rules payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }

        var attributes = AttributeRulesSectionCodec.decode(buffer);
        var components = ComponentRulesSectionCodec.decode(buffer);
        if (buffer.isReadable()) {
            throw new DecoderException("Composition-rules payload contains trailing data");
        }

        return new CompositionRulesPayload(new CompositionRules(attributes, components));
    }
}
