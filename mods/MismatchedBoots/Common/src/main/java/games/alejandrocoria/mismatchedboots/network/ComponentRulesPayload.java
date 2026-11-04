package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public record ComponentRulesPayload(ComponentRules rules) implements CustomPacketPayload {
    public static final Type<ComponentRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "component_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentRulesPayload> STREAM_CODEC =
            StreamCodec.of(ComponentRulesPayload::encode, ComponentRulesPayload::decode);

    public ComponentRulesPayload {
        Objects.requireNonNull(rules, "rules");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ComponentRulesPayload payload) {
        ComponentRulesSectionCodec.encode(buffer, payload.rules);
    }

    private static ComponentRulesPayload decode(RegistryFriendlyByteBuf buffer) {
        ComponentRules rules = ComponentRulesSectionCodec.decode(buffer);
        if (buffer.isReadable()) {
            throw new DecoderException("Component-rules payload contains trailing data");
        }

        return new ComponentRulesPayload(rules);
    }
}
