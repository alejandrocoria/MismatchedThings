package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.MenuScreens;

public class MismatchedBootsClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MismatchedBootsClient.init();
        MenuScreens.register(ModContent.mismatchedWorkbenchMenuType().orElseThrow(),
                MismatchedWorkbenchScreen::new);
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> CompositionRulesClientLifecycle.onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> CompositionRulesClientLifecycle.onDisconnected());
    }
}
