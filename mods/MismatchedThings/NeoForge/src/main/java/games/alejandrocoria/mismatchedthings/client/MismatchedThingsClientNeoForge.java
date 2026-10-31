package games.alejandrocoria.mismatchedthings.client;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import games.alejandrocoria.mismatchedthings.internal.client.tooltip.NeoForgeTooltipSeparators;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MismatchedThingsClientNeoForge {
    private MismatchedThingsClientNeoForge() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(WorkbenchContent.menuType(), WorkbenchScreen::new);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(NeoForgeTooltipSeparators::gather);
    }
}
