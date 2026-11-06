package games.alejandrocoria.mismatchedleggings.data;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenModifiersTest {
    @Test
    void unionPreservesIndependentEffectsWithoutStacking() {
        var eternal = new ForbiddenModifiers(true, false);
        var soulbound = new ForbiddenModifiers(false, true);
        var both = new ForbiddenModifiers(true, true);
        assertEquals(both, eternal.union(soulbound));
        assertEquals(both, soulbound.union(eternal));
        for (var state : List.of(ForbiddenModifiers.EMPTY, eternal, soulbound, both)) {
            assertEquals(state, state.union(state));
            assertEquals(state, state.union(ForbiddenModifiers.EMPTY));
        }
        assertTrue(ForbiddenModifiers.EMPTY.isEmpty());
        assertFalse(eternal.isEmpty());
        assertFalse(soulbound.isEmpty());
    }

    @Test
    void bothCodecsPreserveEveryStateWithoutNativeRegistries() {
        for (boolean eternal : List.of(false, true)) {
            for (boolean soulbound : List.of(false, true)) {
                var state = new ForbiddenModifiers(eternal, soulbound);
                var encoded = ForbiddenModifiers.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
                assertEquals(state, ForbiddenModifiers.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                try {
                    ForbiddenModifiers.STREAM_CODEC.encode(buffer, state);
                    assertEquals(state, ForbiddenModifiers.STREAM_CODEC.decode(buffer));
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
