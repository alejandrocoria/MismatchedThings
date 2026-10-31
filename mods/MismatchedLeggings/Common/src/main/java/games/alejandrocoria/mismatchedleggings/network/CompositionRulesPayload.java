package games.alejandrocoria.mismatchedleggings.network;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSupport;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentNumericKind;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CompositionRulesPayload(CompositionRules rules) implements CustomPacketPayload {
    private static final int MAX_RULE_COUNT = 4_096;
    private static final int MAX_IDENTIFIER_LENGTH = 32_767;
    private static final int MAX_POLICY_LENGTH = 16;
    private static final int MAX_NUMBER_LENGTH = 8;

    public static final Type<CompositionRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "composition_rules"));
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
        var attributes = payload.rules.attributes();
        if (attributes.rules().size() > MAX_RULE_COUNT) {
            throw new EncoderException("Too many attribute rules");
        }
        buffer.writeUtf(attributes.fallback().serializedName(), MAX_POLICY_LENGTH);
        buffer.writeVarInt(attributes.rules().size());
        attributes.rules().forEach((id, policy) -> {
            buffer.writeUtf(id.toString(), MAX_IDENTIFIER_LENGTH);
            buffer.writeUtf(policy.serializedName(), MAX_POLICY_LENGTH);
        });
        var components = payload.rules.components();
        buffer.writeUtf(components.durability().serializedName(), MAX_POLICY_LENGTH);
        buffer.writeUtf(components.repairCost().serializedName(), MAX_POLICY_LENGTH);
        int count = components.declarations().size();
        if (count > MAX_RULE_COUNT) {
            throw new EncoderException("Too many component-rule declarations: " + count);
        }

        buffer.writeVarInt(count);
        components.declarations().forEach((id, rule) -> {
            buffer.writeUtf(id.toString(), MAX_IDENTIFIER_LENGTH);
            buffer.writeUtf(rule.policy().serializedName(), MAX_POLICY_LENGTH);
            if (rule.number() == null) {
                buffer.writeByte(0);
            } else {
                buffer.writeByte(1);
                buffer.writeUtf(rule.number().serializedName(), MAX_NUMBER_LENGTH);
            }
        });
    }

    private static CompositionRulesPayload decode(RegistryFriendlyByteBuf buffer) {
        AttributePolicy fallback = readAttributePolicy(buffer);
        int attributeCount = buffer.readVarInt();
        if (attributeCount < 0 || attributeCount > MAX_RULE_COUNT) {
            throw new DecoderException("Invalid attribute-rule count: " + attributeCount);
        }

        Map<Identifier, AttributePolicy> attributes = new LinkedHashMap<>();
        for (int index = 0; index < attributeCount; index++) {
            Identifier id = readIdentifier(buffer);
            if (attributes.putIfAbsent(id, readAttributePolicy(buffer)) != null) {
                throw new DecoderException("Duplicate attribute ID: " + id);
            }
        }

        DurabilityPolicy durability = readDurability(buffer);
        RepairCostPolicy repairCost = readRepairCost(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_RULE_COUNT) {
            throw new DecoderException("Invalid component-rule count: " + count);
        }

        LinkedHashMap<Identifier, ComponentRule> declarations = new LinkedHashMap<>();
        LinkedHashMap<Identifier, ComponentRule> effective = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            Identifier id = readIdentifier(buffer);
            ComponentRule rule = readRule(buffer);
            if (declarations.putIfAbsent(id, rule) != null) {
                throw new DecoderException("Duplicate component ID: " + id);
            }

            var registered = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id);
            validateShape(id, registered.orElse(null), rule);
            registered.ifPresent(type -> validateRegistered(id, type, rule, effective));
        }

        if (buffer.isReadable()) {
            throw new DecoderException("Composition-rules payload contains trailing data");
        }

        return new CompositionRulesPayload(new CompositionRules(
                new AttributeRuleSet(fallback, attributes),
                new ComponentRules(durability, repairCost, declarations, new ComponentRuleSet(effective))));
    }

    private static AttributePolicy readAttributePolicy(RegistryFriendlyByteBuf buffer) {
        String serialized = buffer.readUtf(MAX_POLICY_LENGTH);
        return AttributePolicy.fromSerializedName(serialized)
                .orElseThrow(() -> new DecoderException("Invalid attribute policy: " + serialized));
    }

    private static DurabilityPolicy readDurability(RegistryFriendlyByteBuf buffer) {
        String serialized = buffer.readUtf(MAX_POLICY_LENGTH);
        return DurabilityPolicy.fromSerializedName(serialized)
                               .orElseThrow(() -> new DecoderException("Invalid durability policy: " + serialized));
    }

    private static RepairCostPolicy readRepairCost(RegistryFriendlyByteBuf buffer) {
        String serialized = buffer.readUtf(MAX_POLICY_LENGTH);
        return RepairCostPolicy.fromSerializedName(serialized)
                               .orElseThrow(() -> new DecoderException("Invalid repair-cost policy: " + serialized));
    }

    private static Identifier readIdentifier(RegistryFriendlyByteBuf buffer) {
        String serialized = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
        int separator = serialized.indexOf(':');
        if (separator <= 0 || separator != serialized.lastIndexOf(':') || separator == serialized.length() - 1) {
            throw new DecoderException("Invalid rule ID: " + serialized);
        }

        try {
            return Identifier.parse(serialized);
        } catch (RuntimeException exception) {
            throw new DecoderException("Invalid rule ID: " + serialized, exception);
        }
    }

    private static ComponentRule readRule(RegistryFriendlyByteBuf buffer) {
        String serializedPolicy = buffer.readUtf(MAX_POLICY_LENGTH);
        ComponentPolicy policy = ComponentPolicy.fromSerializedName(serializedPolicy)
                                                .orElseThrow(() -> new DecoderException("Invalid component policy: " + serializedPolicy));

        int marker = buffer.readUnsignedByte();
        if (marker != 0 && marker != 1) {
            throw new DecoderException("Invalid component numeric-kind marker: " + marker);
        }

        ComponentNumericKind number = null;
        if (marker == 1) {
            String serializedNumber = buffer.readUtf(MAX_NUMBER_LENGTH);
            number = ComponentNumericKind.fromSerializedName(serializedNumber)
                                         .orElseThrow(() -> new DecoderException("Invalid component numeric kind: " + serializedNumber));
        }

        if (policy.isNumeric() && number == null) {
            return ComponentRule.of(policy);
        }
        if (!policy.isNumeric() && number != null) {
            throw new DecoderException("Non-numeric component policy has a numeric kind: " + policy.serializedName());
        }

        return number == null ? ComponentRule.of(policy) : ComponentRule.numeric(policy, number);
    }

    private static void validateShape(Identifier id, DataComponentType<?> type, ComponentRule rule) {
        if (!rule.policy().isNumeric()) {
            return;
        }
        if (type == DataComponents.ENCHANTABLE) {
            if (rule.number() != null) {
                throw new DecoderException("Dedicated component rule must not declare a numeric kind: " + id);
            }
        } else if (rule.number() == null) {
            throw new DecoderException("Generic numeric component rule is missing its numeric kind: " + id);
        }
    }

    private static void validateRegistered(
            Identifier id,
            DataComponentType<?> type,
            ComponentRule rule,
            Map<Identifier, ComponentRule> effective) {
        var resolved = ComponentRuleSupport.resolve(type, rule);
        if (resolved.isEmpty()) {
            throw new DecoderException("Component is fixed and cannot have a generic rule: " + id);
        }
        if (!resolved.orElseThrow().equals(rule)) {
            throw new DecoderException("Unsupported component rule for " + id + ": " + rule);
        }

        effective.put(id, rule);
    }
}
