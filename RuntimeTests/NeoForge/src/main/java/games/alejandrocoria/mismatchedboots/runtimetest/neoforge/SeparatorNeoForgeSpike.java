package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorClientSpike;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

final class SeparatorNeoForgeSpike {
    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SeparatorClientSpike.tick(Minecraft.getInstance()));
    }
}
