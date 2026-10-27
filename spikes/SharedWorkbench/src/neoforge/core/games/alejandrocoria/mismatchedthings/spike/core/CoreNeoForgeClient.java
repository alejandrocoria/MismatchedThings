package games.alejandrocoria.mismatchedthings.spike.core;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class CoreNeoForgeClient {
    private CoreNeoForgeClient() {
    }

    public static void screens(RegisterMenuScreensEvent event) {
        event.register(WorkbenchCore.mismatchedWorkbenchMenuType().orElseThrow(), WorkbenchScreen::new);
    }
}
