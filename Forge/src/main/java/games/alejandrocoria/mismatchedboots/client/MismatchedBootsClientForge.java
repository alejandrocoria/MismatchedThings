package games.alejandrocoria.mismatchedboots.client;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class MismatchedBootsClientForge {
    public static void onClientSetup(FMLClientSetupEvent event) {
        MismatchedBootsClient.init();
    }
}
