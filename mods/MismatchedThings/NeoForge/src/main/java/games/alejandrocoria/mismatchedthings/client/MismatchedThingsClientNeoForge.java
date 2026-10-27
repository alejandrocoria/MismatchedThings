package games.alejandrocoria.mismatchedthings.client;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class MismatchedThingsClientNeoForge {
    private MismatchedThingsClientNeoForge() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(WorkbenchContent.menuType(), WorkbenchScreen::new);
    }
}
