package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public record AttributeRulesPayload(AttributeRuleSet rules) implements CustomPacketPayload {
    public static final Type<AttributeRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "attribute_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttributeRulesPayload> STREAM_CODEC =
            StreamCodec.of(AttributeRulesPayload::encode, AttributeRulesPayload::decode);

    public AttributeRulesPayload {
        Objects.requireNonNull(rules, "rules");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AttributeRulesPayload payload) {
        AttributeRulesSectionCodec.encode(buffer, payload.rules);
    }

    private static AttributeRulesPayload decode(RegistryFriendlyByteBuf buffer) {
        AttributeRuleSet rules = AttributeRulesSectionCodec.decode(buffer);
        if (buffer.isReadable()) {
            throw new DecoderException("Attribute-rules payload contains trailing data");
        }

        return new AttributeRulesPayload(rules);
    }
}
