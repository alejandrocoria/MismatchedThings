package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AttributeRulesPayload(AttributeRuleSet rules) implements CustomPacketPayload {
    private static final int MAX_POLICY_LENGTH = 16;
    private static final int MAX_IDENTIFIER_LENGTH = 32_767;

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
        writePolicy(buffer, payload.rules.fallback());
        buffer.writeVarInt(payload.rules.rules().size());
        payload.rules.rules().forEach((id, policy) -> {
            buffer.writeUtf(id.toString(), MAX_IDENTIFIER_LENGTH);
            writePolicy(buffer, policy);
        });
    }

    private static AttributeRulesPayload decode(RegistryFriendlyByteBuf buffer) {
        AttributePolicy fallback = readPolicy(buffer);
        int count = buffer.readVarInt();
        if (count < 0) {
            throw new DecoderException("Negative attribute-rule count: " + count);
        }

        Map<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String serializedId = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
            Identifier id;
            try {
                id = Identifier.parse(serializedId);
            } catch (RuntimeException exception) {
                throw new DecoderException("Invalid attribute ID: " + serializedId, exception);
            }
            AttributePolicy policy = readPolicy(buffer);
            if (rules.putIfAbsent(id, policy) != null) {
                throw new DecoderException("Duplicate attribute ID: " + id);
            }
        }
        if (buffer.isReadable()) {
            throw new DecoderException("Attribute-rules payload contains trailing data");
        }
        return new AttributeRulesPayload(new AttributeRuleSet(fallback, rules));
    }

    private static void writePolicy(RegistryFriendlyByteBuf buffer, AttributePolicy policy) {
        buffer.writeUtf(policy.serializedName(), MAX_POLICY_LENGTH);
    }

    private static AttributePolicy readPolicy(RegistryFriendlyByteBuf buffer) {
        String serialized = buffer.readUtf(MAX_POLICY_LENGTH);
        return AttributePolicy.fromSerializedName(serialized)
                .orElseThrow(() -> new DecoderException("Invalid attribute policy: " + serialized));
    }
}
