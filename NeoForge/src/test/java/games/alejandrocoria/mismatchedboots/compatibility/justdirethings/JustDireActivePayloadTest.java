package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JustDireActivePayloadTest {
    @Test void settingsTransportPreservesIdentityOrderingAndUnsupportedPreferencesForServerValidation() {
        var request = new JustDireActiveNetwork.SettingsRequest(12, 36, 3, 4, UUID.randomUUID(),
                JustDireActiveNetwork.BINDING, "groundstomp", 2, 71, true, false);
        var buffer = buffer();
        try {
            JustDireActiveNetwork.SettingsRequest.CODEC.encode(buffer, request);
            assertEquals(request, JustDireActiveNetwork.SettingsRequest.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void allRoutesRejectTrailingDataInsteadOfAcceptingAmbiguousFrames() {
        rejectTrailing(JustDireActiveNetwork.Prepare.CODEC, new JustDireActiveNetwork.Prepare(1, 71, false));
        rejectTrailing(JustDireActiveNetwork.Activate.CODEC, new JustDireActiveNetwork.Activate(1, UUID.randomUUID(), 71, false));
        rejectTrailing(JustDireActiveNetwork.ActivationReply.CODEC, new JustDireActiveNetwork.ActivationReply(1, UUID.randomUUID(), true, true));
        rejectTrailing(JustDireActiveNetwork.SettingsRequest.CODEC, new JustDireActiveNetwork.SettingsRequest(1, 36, 1, 2,
                UUID.randomUUID(), 2, "groundstomp", 5, 71, false, true));
        rejectTrailing(JustDireActiveNetwork.SettingsReply.CODEC, new JustDireActiveNetwork.SettingsReply(1, 2, 3, UUID.randomUUID(), true));
    }

    @Test void abilityNameLengthIsBoundedBeforeItCanReachSettingsLookup() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(1); buffer.writeVarInt(36); buffer.writeVarInt(1); buffer.writeVarInt(2);
            buffer.writeUUID(UUID.randomUUID()); buffer.writeVarInt(2); buffer.writeUtf("x".repeat(33));
            buffer.writeVarInt(5); buffer.writeVarInt(71); buffer.writeBoolean(false); buffer.writeBoolean(true);
            assertThrows(DecoderException.class, () -> JustDireActiveNetwork.SettingsRequest.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    private static <T> void rejectTrailing(StreamCodec<RegistryFriendlyByteBuf, T> codec, T packet) {
        var buffer = buffer();
        try { codec.encode(buffer, packet); buffer.writeByte(1); assertThrows(DecoderException.class, () -> codec.decode(buffer)); }
        finally { buffer.release(); }
    }
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
