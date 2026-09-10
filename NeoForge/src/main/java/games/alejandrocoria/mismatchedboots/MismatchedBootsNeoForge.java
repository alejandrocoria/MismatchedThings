package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(Constants.MOD_ID)
public class MismatchedBootsNeoForge {
    public MismatchedBootsNeoForge(IEventBus eventBus) {
        MismatchedBoots.init();
        eventBus.addListener((FMLClientSetupEvent event) -> MismatchedBootsClientNeoForge.onClientSetup(event, eventBus));
    }
}
