package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentRulesPayloadTest {
    private static final Identifier DORMANT = Identifier.parse("futuremod:dormant_energy");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        ComponentRuleState.restoreDefaults();
    }

    @Test
    void codecIsDeterministicAndPreservesCompleteOrderedState() {
        ComponentRules rules = rules();

        byte[] first = encode(new ComponentRulesPayload(rules));
        byte[] second = encode(new ComponentRulesPayload(rules));
        ComponentRules decoded = decode(first).rules();

        assertArrayEquals(first, second);
        assertEquals(rules, decoded);
        assertEquals(rules.declarations().keySet().stream().toList(),
                decoded.declarations().keySet().stream().toList());
        assertEquals(ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG),
                decoded.declarations().get(DORMANT));
        assertFalse(decoded.components().rules().containsKey(DORMANT));
    }

    @Test
    void wireFormatStartsWithDedicatedPoliciesAndContainsNoVersionField() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ComponentRulesPayload.STREAM_CODEC.encode(buffer, new ComponentRulesPayload(rules()));

            assertEquals("separate", buffer.readUtf(16));
            assertEquals("owner", buffer.readUtf(16));
            assertEquals(3, buffer.readVarInt());
            assertEquals("minecraft:enchantable", buffer.readUtf());
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecRejectsInvalidCountsIdsAndDuplicates() {
        assertMalformed(buffer -> writeHeader(buffer, "average", "maximum", -1));
        assertMalformed(buffer -> writeHeader(buffer, "average", "maximum", 4_097));
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "missing_namespace", "owner", null);
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 2);
            writeRule(buffer, "minecraft:rarity", "same", null);
            writeRule(buffer, "minecraft:rarity", "owner", null);
        });
    }

    @Test
    void codecRejectsInvalidPoliciesNumericKindsAndMarkers() {
        assertMalformed(buffer -> writeHeader(buffer, "unknown", "maximum", 0));
        assertMalformed(buffer -> writeHeader(buffer, "average", "unknown", 0));
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            buffer.writeUtf("futuremod:dormant");
            buffer.writeUtf("unknown");
            buffer.writeByte(0);
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            buffer.writeUtf("futuremod:dormant");
            buffer.writeUtf("average");
            buffer.writeByte(1);
            buffer.writeUtf("double");
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            buffer.writeUtf("futuremod:dormant");
            buffer.writeUtf("owner");
            buffer.writeByte(2);
        });
    }

    @Test
    void codecRejectsInvalidDeclarationShapesAndRegisteredAdapters() {
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "futuremod:dormant", "average", null);
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "futuremod:dormant", "share", "int");
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "minecraft:enchantable", "average", "int");
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "minecraft:rarity", "intersection", null);
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "average", "maximum", 1);
            writeRule(buffer, "minecraft:repair_cost", "owner", null);
        });
    }

    @Test
    void codecRejectsTruncationAndTrailingData() {
        byte[] complete = encode(new ComponentRulesPayload(rules()));
        assertThrows(RuntimeException.class,
                () -> decode(Arrays.copyOf(complete, complete.length - 1)));

        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ComponentRulesPayload.STREAM_CODEC.encode(buffer, new ComponentRulesPayload(rules()));
            buffer.writeByte(0);
            assertThrows(RuntimeException.class, () -> ComponentRulesPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void malformedDecodeCannotPartiallyReplaceInstalledState() {
        ComponentRules installed = rules();
        ComponentRuleState.install(installed);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            writeHeader(buffer, "average", "owner", 2);
            writeRule(buffer, "minecraft:rarity", "owner", null);
            writeRule(buffer, "minecraft:rarity", "same", null);

            assertThrows(RuntimeException.class, () -> ComponentRulesPayload.STREAM_CODEC.decode(buffer));
            assertSame(installed, ComponentRuleState.current());
        } finally {
            buffer.release();
        }
    }

    private static ComponentRules rules() {
        LinkedHashMap<Identifier, ComponentRule> declarations = new LinkedHashMap<>();
        declarations.put(Identifier.withDefaultNamespace("enchantable"),
                ComponentRule.of(ComponentPolicy.MAXIMUM));
        declarations.put(Identifier.withDefaultNamespace("rarity"),
                ComponentRule.of(ComponentPolicy.OWNER));
        declarations.put(DORMANT, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG));

        LinkedHashMap<Identifier, ComponentRule> effective = new LinkedHashMap<>();
        effective.put(Identifier.withDefaultNamespace("enchantable"),
                ComponentRule.of(ComponentPolicy.MAXIMUM));
        effective.put(Identifier.withDefaultNamespace("rarity"),
                ComponentRule.of(ComponentPolicy.OWNER));
        return new ComponentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER,
                declarations, new ComponentRuleSet(effective));
    }

    private static byte[] encode(ComponentRulesPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ComponentRulesPayload.STREAM_CODEC.encode(buffer, payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static ComponentRulesPayload decode(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), builtInRegistryAccess());
        try {
            return ComponentRulesPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertMalformed(BufferWriter writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            writer.write(buffer);
            assertThrows(RuntimeException.class, () -> ComponentRulesPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void writeHeader(
            RegistryFriendlyByteBuf buffer, String durability, String repairCost, int count) {
        buffer.writeUtf(durability, 16);
        buffer.writeUtf(repairCost, 16);
        buffer.writeVarInt(count);
    }

    private static void writeRule(
            RegistryFriendlyByteBuf buffer, String id, String policy, String number) {
        buffer.writeUtf(id);
        buffer.writeUtf(policy, 16);
        buffer.writeByte(number == null ? 0 : 1);
        if (number != null) {
            buffer.writeUtf(number, 8);
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), builtInRegistryAccess());
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }

    @FunctionalInterface
    private interface BufferWriter {
        void write(RegistryFriendlyByteBuf buffer);
    }
}
