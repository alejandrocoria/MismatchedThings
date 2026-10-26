package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JustDireActivePayloadTest {
    @Test
    void settingsTransportPreservesIdentityOrderingAndUnsupportedPreferencesForServerValidation() {
        var request = new JustDireActiveNetwork.SettingsRequest(12, 36, 3, 4, UUID.randomUUID(),
                                                                JustDireActiveNetwork.BINDING, "groundstomp", 2, 71, true, false);
        var buffer = buffer();
        try {
            JustDireActiveNetwork.SettingsRequest.CODEC.encode(buffer, request);
            assertEquals(request, JustDireActiveNetwork.SettingsRequest.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void allRoutesRejectTrailingDataInsteadOfAcceptingAmbiguousFrames() {
        rejectTrailing(JustDireActiveNetwork.Prepare.CODEC, new JustDireActiveNetwork.Prepare(1, 71, false));
        rejectTrailing(JustDireActiveNetwork.Activate.CODEC,
                       new JustDireActiveNetwork.Activate(1, UUID.randomUUID(), 71, false, java.util.List.of(36)));
        rejectTrailing(JustDireActiveNetwork.ActivationReply.CODEC,
                       new JustDireActiveNetwork.ActivationReply(1, UUID.randomUUID(), true, true, List.of(9, 36)));
        rejectTrailing(JustDireActiveNetwork.SettingsRequest.CODEC, new JustDireActiveNetwork.SettingsRequest(1,
                                                                                                              36,
                                                                                                              1,
                                                                                                              2,
                                                                                                              UUID.randomUUID(),
                                                                                                              2,
                                                                                                              "groundstomp",
                                                                                                              5,
                                                                                                              71,
                                                                                                              false,
                                                                                                              true));
        rejectTrailing(JustDireActiveNetwork.SettingsReply.CODEC, new JustDireActiveNetwork.SettingsReply(1, 2, 3, UUID.randomUUID(), true));
    }

    @Test
    void abilityNameLengthIsBoundedBeforeItCanReachSettingsLookup() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(1);
            buffer.writeVarInt(36);
            buffer.writeVarInt(1);
            buffer.writeVarInt(2);
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeVarInt(2);
            buffer.writeUtf("x".repeat(33));
            buffer.writeVarInt(5);
            buffer.writeVarInt(71);
            buffer.writeBoolean(false);
            buffer.writeBoolean(true);
            assertThrows(DecoderException.class, () -> JustDireActiveNetwork.SettingsRequest.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void activationSubsetRoundTripsAndRejectsMalformedSlotsBeforeDispatch() {
        var packet = new JustDireActiveNetwork.Activate(8, UUID.randomUUID(), 4, true, List.of(40, 9, 36, 0));
        var valid = buffer();
        try {
            JustDireActiveNetwork.Activate.CODEC.encode(valid, packet);
            assertEquals(packet, JustDireActiveNetwork.Activate.CODEC.decode(valid));
        } finally {
            valid.release();
        }
        for (var slots : List.of(List.<Integer>of(), packet.slots())) {
            var reply = new JustDireActiveNetwork.ActivationReply(8, packet.nonce(), true, true, slots);
            var encoded = buffer();
            try {
                JustDireActiveNetwork.ActivationReply.CODEC.encode(encoded, reply);
                assertEquals(reply, JustDireActiveNetwork.ActivationReply.CODEC.decode(encoded));
            } finally {
                encoded.release();
            }
        }
        for (var slots : List.of(List.<Integer>of(), List.of(9, 9), List.of(-1), List.of(37), List.of(38),
                                 List.of(39), List.of(41), List.of(Integer.MAX_VALUE))) {
            var malformed = buffer();
            try {
                JustDireActiveNetwork.Activate.CODEC.encode(malformed,
                                                            new JustDireActiveNetwork.Activate(1, packet.nonce(), 4, true, slots));
                assertThrows(DecoderException.class, () -> JustDireActiveNetwork.Activate.CODEC.decode(malformed));
            } finally {
                malformed.release();
            }
            if (!slots.isEmpty()) {
                var malformedReply = buffer();
                try {
                    JustDireActiveNetwork.ActivationReply.CODEC.encode(malformedReply,
                                                                       new JustDireActiveNetwork.ActivationReply(1,
                                                                                                                 packet.nonce(),
                                                                                                                 true,
                                                                                                                 true,
                                                                                                                 slots));
                    assertThrows(DecoderException.class, () -> JustDireActiveNetwork.ActivationReply.CODEC.decode(malformedReply));
                } finally {
                    malformedReply.release();
                }
            }
        }
        // Length is checked before allocating/reading any purported entries.
        for (int size : new int[]{-1, 42, Integer.MAX_VALUE}) {
            var malformed = buffer();
            try {
                malformed.writeVarInt(1);
                malformed.writeUUID(packet.nonce());
                malformed.writeVarInt(4);
                malformed.writeBoolean(true);
                malformed.writeVarInt(size);
                assertThrows(DecoderException.class, () -> JustDireActiveNetwork.Activate.CODEC.decode(malformed));
            } finally {
                malformed.release();
            }
        }
    }

    private static <T> void rejectTrailing(StreamCodec<RegistryFriendlyByteBuf, T> codec, T packet) {
        var buffer = buffer();
        try {
            codec.encode(buffer, packet);
            buffer.writeByte(1);
            assertThrows(DecoderException.class, () -> codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
