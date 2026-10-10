package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientNeoForge;
import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(Constants.MOD_ID)
public class MismatchedBootsNeoForge {
    public MismatchedBootsNeoForge(IEventBus eventBus) {
        ModRegistries.register(eventBus);
        MismatchedBoots.init();
        IceAndFireTideCompatibility.initialize();
        JustDireResourcesCompatibility.initialize(eventBus);
        JustDireUpgradesCompatibility.initialize(eventBus);
        JustDireAttributesCompatibility.initialize(eventBus);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            AttributeRulesLifecycle.onServerStarting();
            ComponentRulesLifecycle.onServerStarting();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            AttributeRulesLifecycle.onServerStopped();
            ComponentRulesLifecycle.onServerStopped();
        });
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) ->
                event.sendRecipes(ModContent.mismatchedWorkbenchRecipeType()));
        eventBus.addListener(MismatchedBootsClientNeoForge::onClientSetup);
        eventBus.addListener(MismatchedBootsClientNeoForge::registerScreens);
    }
}
