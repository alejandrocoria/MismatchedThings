package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class MismatchedBootsClientForge {
    public static void onClientSetup(FMLClientSetupEvent event) {
        MismatchedBootsClient.init();
        event.enqueueWork(() -> MenuScreens.register(
                ModContent.mismatchedWorkbenchMenuType().orElseThrow(), MismatchedWorkbenchScreen::new));
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(
                login -> CompositionRulesClientLifecycle.onConnected());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
                logout -> CompositionRulesClientLifecycle.onDisconnected());
    }
}
