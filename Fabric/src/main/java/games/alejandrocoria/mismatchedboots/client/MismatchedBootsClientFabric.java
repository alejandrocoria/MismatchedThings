package games.alejandrocoria.mismatchedboots.client;

import net.fabricmc.api.ClientModInitializer;

public class MismatchedBootsClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MismatchedBootsClient.init();
    }
}
