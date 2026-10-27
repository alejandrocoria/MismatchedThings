package games.alejandrocoria.mismatchedthings.spike.core;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class CoreFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(WorkbenchCore.mismatchedWorkbenchMenuType().orElseThrow(), WorkbenchScreen::new);
    }
}
