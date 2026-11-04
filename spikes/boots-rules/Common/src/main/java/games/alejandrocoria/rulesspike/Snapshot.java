package games.alejandrocoria.rulesspike;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.network.AttributeRulesPayload;
import games.alejandrocoria.mismatchedboots.network.ComponentRulesPayload;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Prototype only: bounded sections reuse the existing strict codecs without changing them. */
public record Snapshot(AttributeRuleSet attributes, ComponentRules components) implements CustomPacketPayload {
    private static final int MAX_BYTES = 524_288;
    public static final Type<Snapshot> TYPE = new Type<>(Identifier.parse("mismatchedboots:rules_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(Snapshot::encode, Snapshot::decode);

    public static Snapshot server() {
        return new Snapshot(AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules());
    }

    @Override
    public Type<Snapshot> type() {
        return TYPE;
    }

    private static <T> void section(RegistryFriendlyByteBuf buffer, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var temporary = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), buffer.registryAccess());
        try {
            codec.encode(temporary, value);
            if (temporary.readableBytes() > MAX_BYTES) {
                throw new EncoderException("Rules section exceeds spike budget");
            }
            buffer.writeVarInt(temporary.readableBytes());
            buffer.writeBytes(temporary);
        } finally {
            temporary.release();
        }
    }

    private static <T> T section(RegistryFriendlyByteBuf buffer, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_BYTES || length > buffer.readableBytes()) {
            throw new DecoderException("Invalid rules section length");
        }
        // Non-retained slice: the enclosing packet owns its lifetime.
        return codec.decode(new RegistryFriendlyByteBuf(buffer.readSlice(length), buffer.registryAccess()));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
        int start = buffer.writerIndex();
        section(buffer, AttributeRulesPayload.STREAM_CODEC, new AttributeRulesPayload(value.attributes));
        section(buffer, ComponentRulesPayload.STREAM_CODEC, new ComponentRulesPayload(value.components));
        if (buffer.writerIndex() - start > MAX_BYTES) {
            throw new EncoderException("Rules snapshot exceeds spike budget");
        }
    }

    private static Snapshot decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("Rules snapshot exceeds spike budget");
        }
        var attributes = section(buffer, AttributeRulesPayload.STREAM_CODEC).rules();
        var components = section(buffer, ComponentRulesPayload.STREAM_CODEC).rules();
        if (buffer.isReadable()) {
            throw new DecoderException("Trailing rules snapshot bytes");
        }
        return new Snapshot(attributes, components);
    }
}
