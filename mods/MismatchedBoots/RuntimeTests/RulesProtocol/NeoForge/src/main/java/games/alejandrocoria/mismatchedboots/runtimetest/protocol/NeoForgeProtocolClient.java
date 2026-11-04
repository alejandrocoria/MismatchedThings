package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import games.alejandrocoria.mismatchedboots.runtimetest.protocol.mixin.ProtocolMismatchAccessor;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen;

@EventBusSubscriber(modid = "mismatchedboots_protocol_test", value = Dist.CLIENT)
public final class NeoForgeProtocolClient {
    @SubscribeEvent
    public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
        ProtocolRejectionClient.joined();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        String reasons = client.screen instanceof ModMismatchDisconnectedScreen screen
                ? ((ProtocolMismatchAccessor) screen).mismatchedboots$channels().toString() : null;
        ProtocolRejectionClient.tick(client, true, reasons);
    }
}
