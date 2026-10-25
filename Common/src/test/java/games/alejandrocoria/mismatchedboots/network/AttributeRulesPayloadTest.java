package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeRulesPayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecIsDeterministicAndPreservesFallbackAndOrderedEntries() {
        AttributeRuleSet rules = rules();

        byte[] first = encode(new AttributeRulesPayload(rules));
        byte[] second = encode(new AttributeRulesPayload(rules));
        AttributeRulesPayload decoded = decode(first);

        assertArrayEquals(first, second);
        assertEquals(rules, decoded.rules());
        assertEquals(
                rules.rules().keySet().stream().toList(),
                decoded.rules().rules().keySet().stream().toList());
    }

    @Test
    void wireFormatStartsWithTheFallbackAndContainsNoVersionField() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            AttributeRulesPayload.STREAM_CODEC.encode(buffer, new AttributeRulesPayload(rules()));

            assertEquals("separate", buffer.readUtf(16));
            assertEquals(3, buffer.readVarInt());
            assertEquals("minecraft:armor", buffer.readUtf());
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecRejectsDuplicateAttributeIds() {
        assertMalformed(buffer -> {
            writeHeader(buffer, "separate", 2);
            writeEntry(buffer, "minecraft:armor", "average");
            writeEntry(buffer, "minecraft:armor", "combine");
        });
    }

    @Test
    void codecRejectsInvalidAttributeIds() {
        for (String id : new String[]{"armor", ":armor", "minecraft:", "a:b:c", "Invalid Namespace:armor"}) {
            assertMalformed(buffer -> {
                writeHeader(buffer, "separate", 1);
                writeEntry(buffer, id, "average");
            });
        }
    }

    @Test
    void codecPreservesEmptyAndMaximumDormantDeclarations() {
        for (int count : new int[]{0, 4_096}) {
            AttributeRuleSet rules = declarations(count);
            assertEquals(rules, decode(encode(new AttributeRulesPayload(rules))).rules());
        }
    }

    @Test
    void encoderRejectsTooManyDeclarationsBeforeWriting() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            assertThrows(EncoderException.class, () -> AttributeRulesPayload.STREAM_CODEC.encode(
                    buffer, new AttributeRulesPayload(declarations(4_097))));
            assertEquals(0, buffer.writerIndex(), "Rejected payload must not write a partial header");
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsOutOfRangeCountsWithoutReadingEntries() {
        for (int count : new int[]{-1, 4_097, Integer.MAX_VALUE}) {
            assertMalformed(buffer -> writeHeader(buffer, "separate", count));
        }
    }

    private static AttributeRuleSet declarations(int count) {
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            rules.put(Identifier.fromNamespaceAndPath("absent_mod", "attribute_" + index), AttributePolicy.COMBINE);
        }
        return new AttributeRuleSet(AttributePolicy.SEPARATE, rules);
    }

    @Test
    void codecRejectsInvalidPolicies() {
        assertMalformed(buffer -> {
            writeHeader(buffer, "unknown", 0);
        });
        assertMalformed(buffer -> {
            writeHeader(buffer, "separate", 1);
            writeEntry(buffer, "minecraft:armor", "unknown");
        });
    }

    @Test
    void codecRejectsTruncation() {
        byte[] complete = encode(new AttributeRulesPayload(rules()));

        assertThrows(RuntimeException.class,
                     () -> decode(Arrays.copyOf(complete, complete.length - 1)));
    }

    @Test
    void codecRejectsTrailingData() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            AttributeRulesPayload.STREAM_CODEC.encode(buffer, new AttributeRulesPayload(rules()));
            buffer.writeByte(0);

            assertThrows(DecoderException.class, () -> AttributeRulesPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void emptyHandshakeRejectsTrailingData() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ClientReadyPayload.STREAM_CODEC.encode(buffer, ClientReadyPayload.INSTANCE);
            assertEquals(0, buffer.readableBytes());

            buffer.writeByte(0);
            assertThrows(RuntimeException.class, () -> ClientReadyPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static AttributeRuleSet rules() {
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        rules.put(Identifier.withDefaultNamespace("armor"), AttributePolicy.AVERAGE);
        rules.put(Identifier.fromNamespaceAndPath("example", "mana"), AttributePolicy.COMBINE);
        rules.put(Identifier.fromNamespaceAndPath("missing", "future_attribute"), AttributePolicy.REJECT);
        return new AttributeRuleSet(AttributePolicy.SEPARATE, rules);
    }

    private static byte[] encode(AttributeRulesPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            AttributeRulesPayload.STREAM_CODEC.encode(buffer, payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static AttributeRulesPayload decode(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), builtInRegistryAccess());
        try {
            return AttributeRulesPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertMalformed(BufferWriter writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            writer.write(buffer);
            assertThrows(DecoderException.class, () -> AttributeRulesPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, String fallback, int count) {
        buffer.writeUtf(fallback, 16);
        buffer.writeVarInt(count);
    }

    private static void writeEntry(RegistryFriendlyByteBuf buffer, String id, String policy) {
        buffer.writeUtf(id);
        buffer.writeUtf(policy, 16);
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
