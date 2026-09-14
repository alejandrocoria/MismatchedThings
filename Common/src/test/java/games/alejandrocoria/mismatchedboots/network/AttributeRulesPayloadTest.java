package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeRulesPayloadTest {
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
            writeEntry(buffer, "armor", "combine");
        });
    }

    @Test
    void codecRejectsInvalidAttributeIds() {
        assertMalformed(buffer -> {
            writeHeader(buffer, "separate", 1);
            writeEntry(buffer, "Invalid Namespace:armor", "average");
        });
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

            assertThrows(RuntimeException.class, () -> AttributeRulesPayload.STREAM_CODEC.decode(buffer));
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
            assertThrows(RuntimeException.class, () -> AttributeRulesPayload.STREAM_CODEC.decode(buffer));
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
