package games.alejandrocoria.mismatchedthings.client;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class MismatchedThingsClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(WorkbenchContent.menuType(), WorkbenchScreen::new);
    }
}
