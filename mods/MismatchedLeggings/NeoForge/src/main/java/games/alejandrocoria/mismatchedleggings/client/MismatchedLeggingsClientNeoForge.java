package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

public final class MismatchedLeggingsClientNeoForge {
    private MismatchedLeggingsClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn login) -> {
                CompositionRuleState.resetPreview();
                ClientPacketDistributor.sendToServer(new ClientReadyPayload());
            });
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut logout) -> CompositionRuleState.resetPreview());
        });
    }
}
