package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionRulesPayloadTest {
    private static final Identifier ENCHANTABLE = Identifier.parse("minecraft:enchantable");
    private static final Identifier DORMANT = Identifier.parse("futuremod:stored_charge");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecPreservesBothSectionsAndDormantDeclarationsWithoutExtraFraming() {
        CompositionRules rules = rules();
        byte[] encoded = encode(rules);
        CompositionRules decoded = decode(encoded);

        assertArrayEquals(legacySections(rules), encoded);
        assertEquals(rules, decoded);
        assertEquals(rules.attributes().rules().keySet().stream().toList(),
                     decoded.attributes().rules().keySet().stream().toList());
        assertEquals(rules.components().declarations().keySet().stream().toList(),
                     decoded.components().declarations().keySet().stream().toList());
        assertEquals(ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG),
                     decoded.components().declarations().get(DORMANT));
        assertFalse(decoded.components().components().rules().containsKey(DORMANT));
    }

    @Test
    void decoderValidatesTheAttributeSectionBeforeReadingComponents() {
        assertMalformed(buffer -> buffer.writeUtf("unknown"));
        assertMalformed(buffer -> {
            buffer.writeUtf("separate");
            buffer.writeVarInt(-1);
        });
        assertMalformed(buffer -> {
            buffer.writeUtf("separate");
            buffer.writeVarInt(1);
            buffer.writeUtf("missing_namespace");
            buffer.writeUtf("average");
        });
        assertMalformed(buffer -> {
            buffer.writeUtf("separate");
            buffer.writeVarInt(2);
            for (int index = 0; index < 2; index++) {
                buffer.writeUtf("minecraft:armor");
                buffer.writeUtf("average");
            }
        });
    }

    @Test
    void decoderRetainsComponentValidationAfterACompleteAttributeSection() {
        assertBadComponents(buffer -> {
            componentHeader(buffer, 4_097);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "futuremod:energy", "unknown", 0, null);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "bad:id:extra", "owner", 0, null);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 2);
            componentEntry(buffer, "futuremod:energy", "owner", 0, null);
            componentEntry(buffer, "futuremod:energy", "same", 0, null);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "futuremod:energy", "owner", 2, null);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "futuremod:energy", "average", 1, "unknown");
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "futuremod:energy", "average", 0, null);
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "minecraft:enchantable", "average", 1, "int");
        });
        assertBadComponents(buffer -> {
            componentHeader(buffer, 1);
            componentEntry(buffer, "minecraft:repair_cost", "owner", 0, null);
        });
    }

    @Test
    void codecRejectsTruncationInEitherSectionAndTrailingBytes() {
        byte[] encoded = encode(rules());
        RegistryFriendlyByteBuf attributes = buffer();
        try {
            AttributeRulesPayload.STREAM_CODEC.encode(attributes, new AttributeRulesPayload(rules().attributes()));
            assertThrows(RuntimeException.class, () -> decode(Arrays.copyOf(encoded, attributes.readableBytes() - 1)));
            assertThrows(RuntimeException.class, () -> decode(Arrays.copyOf(encoded, encoded.length - 1)));
            assertThrows(DecoderException.class, () -> decode(Arrays.copyOf(encoded, encoded.length + 1)));
        } finally {
            attributes.release();
        }
    }

    @Test
    void totalBudgetRejectsCombinedSectionsWithoutTouchingTheOutgoingPacketOrReadingInput() {
        LinkedHashMap<Identifier, AttributePolicy> attributes = new LinkedHashMap<>();
        LinkedHashMap<Identifier, ComponentRule> components = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            attributes.put(longId("attribute_" + index, 27_000), AttributePolicy.COMBINE);
            components.put(longId("component_" + index, 27_000), ComponentRule.of(ComponentPolicy.OWNER));
        }
        CompositionRules rules = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, attributes),
                new ComponentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER, components, new ComponentRuleSet(Map.of())));
        byte[] combined = legacySections(rules);
        assertTrue(combined.length > CompositionRulesPayload.MAX_PAYLOAD_BYTES);

        RegistryFriendlyByteBuf outgoing = buffer();
        RegistryFriendlyByteBuf incoming = buffer();
        try {
            outgoing.writeByte(17);
            assertThrows(EncoderException.class,
                         () -> CompositionRulesPayload.STREAM_CODEC.encode(outgoing, new CompositionRulesPayload(rules)));
            assertEquals(1, outgoing.writerIndex(), "Rejected encoding must leave the existing packet untouched");
            assertEquals(17, outgoing.getUnsignedByte(0));

            incoming.writeBytes(combined);
            assertThrows(DecoderException.class, () -> CompositionRulesPayload.STREAM_CODEC.decode(incoming));
            assertEquals(0, incoming.readerIndex(), "Oversized input must be rejected before reading either section");
        } finally {
            outgoing.release();
            incoming.release();
        }
    }

    @Test
    void byteBudgetIncludesTheExactLimitButNotOneExtraByte() {
        CompositionRules atLimit = rulesWithWireSize(CompositionRulesPayload.MAX_PAYLOAD_BYTES);
        byte[] encoded = encode(atLimit);
        assertEquals(CompositionRulesPayload.MAX_PAYLOAD_BYTES, encoded.length);
        assertTrue(atLimit.equals(decode(encoded)), "Boundary snapshot did not round-trip intact");

        CompositionRules tooLarge = rulesWithWireSize(CompositionRulesPayload.MAX_PAYLOAD_BYTES + 1);
        assertEquals(CompositionRulesPayload.MAX_PAYLOAD_BYTES + 1, legacySections(tooLarge).length);
        assertThrows(EncoderException.class, () -> encode(tooLarge));
        assertThrows(DecoderException.class, () -> decode(legacySections(tooLarge)));
    }

    @Test
    void encoderRetainsBothSectionDeclarationCountLimits() {
        LinkedHashMap<Identifier, AttributePolicy> attributes = new LinkedHashMap<>();
        LinkedHashMap<Identifier, ComponentRule> components = new LinkedHashMap<>();
        for (int index = 0; index < 4_097; index++) {
            Identifier id = Identifier.parse("absent:rule_" + index);
            attributes.put(id, AttributePolicy.COMBINE);
            components.put(id, ComponentRule.of(ComponentPolicy.OWNER));
        }
        CompositionRules defaults = rules();
        assertThrows(EncoderException.class, () -> encode(new CompositionRules(
                new AttributeRuleSet(AttributePolicy.SEPARATE, attributes), defaults.components())));
        assertThrows(EncoderException.class, () -> encode(new CompositionRules(defaults.attributes(),
                new ComponentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER,
                                   components, new ComponentRuleSet(Map.of())))));
    }

    private static CompositionRules rules() {
        LinkedHashMap<Identifier, AttributePolicy> attributes = new LinkedHashMap<>();
        attributes.put(Identifier.parse("minecraft:armor"), AttributePolicy.AVERAGE);
        attributes.put(Identifier.parse("futuremod:attribute"), AttributePolicy.MAXIMUM);
        LinkedHashMap<Identifier, ComponentRule> declarations = new LinkedHashMap<>();
        declarations.put(ENCHANTABLE, ComponentRule.of(ComponentPolicy.MAXIMUM));
        declarations.put(DORMANT, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG));
        return new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, attributes),
                new ComponentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER, declarations,
                        new ComponentRuleSet(Map.of(ENCHANTABLE, ComponentRule.of(ComponentPolicy.MAXIMUM)))));
    }

    private static CompositionRules rulesWithWireSize(int target) {
        LinkedHashMap<Identifier, AttributePolicy> attributes = new LinkedHashMap<>();
        for (int index = 0; index < 15; index++) {
            attributes.put(longId("rule_" + index, 32_767), AttributePolicy.AVERAGE);
        }
        Identifier tail = longId("tail", 16_384);
        attributes.put(tail, AttributePolicy.AVERAGE);
        ComponentRules components = new ComponentRules(
                DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER, new ComponentRuleSet(Map.of()));
        CompositionRules base = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, attributes), components);
        int padding = target - legacySections(base).length;
        // Both tail lengths use a three-byte UTF length prefix, so padding changes only the actual ID bytes.
        attributes.remove(tail);
        attributes.put(longId("tail", 16_384 + padding), AttributePolicy.AVERAGE);
        return new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, attributes), components);
    }

    private static Identifier longId(String name, int length) {
        String prefix = "absent:" + name + "_";
        return Identifier.parse(prefix + "x".repeat(length - prefix.length()));
    }

    private static void assertBadComponents(Consumer<RegistryFriendlyByteBuf> writer) {
        assertMalformed(buffer -> {
            AttributeRulesPayload.STREAM_CODEC.encode(buffer, new AttributeRulesPayload(rules().attributes()));
            writer.accept(buffer);
        });
    }

    private static void assertMalformed(Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            writer.accept(buffer);
            assertThrows(DecoderException.class, () -> CompositionRulesPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void componentHeader(RegistryFriendlyByteBuf buffer, int count) {
        buffer.writeUtf("separate");
        buffer.writeUtf("owner");
        buffer.writeVarInt(count);
    }

    private static void componentEntry(RegistryFriendlyByteBuf buffer, String id, String policy, int marker, String number) {
        buffer.writeUtf(id);
        buffer.writeUtf(policy);
        buffer.writeByte(marker);
        if (number != null) {
            buffer.writeUtf(number);
        }
    }

    private static byte[] encode(CompositionRules rules) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            CompositionRulesPayload.STREAM_CODEC.encode(buffer, new CompositionRulesPayload(rules));
            return bytes(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte[] legacySections(CompositionRules rules) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            AttributeRulesPayload.STREAM_CODEC.encode(buffer, new AttributeRulesPayload(rules.attributes()));
            ComponentRulesPayload.STREAM_CODEC.encode(buffer, new ComponentRulesPayload(rules.components()));
            return bytes(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte[] bytes(RegistryFriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    private static CompositionRules decode(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeBytes(bytes);
            return CompositionRulesPayload.STREAM_CODEC.decode(buffer).rules();
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    }
}
