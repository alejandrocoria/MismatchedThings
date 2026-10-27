package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientNeoForge;
import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireHeldToggleCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.neovitae.NeoVitaeInfusionCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCures;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefense;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearEffects;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
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
        JustDireRefundCompatibility.initialize(eventBus);
        MismatchedBoots.init();
        IceAndFireTideCompatibility.initialize();
        JustDireResourcesCompatibility.initialize(eventBus);
        JustDireUpgradesCompatibility.initialize(eventBus);
        JustDireGroundStompCompatibility.initialize(eventBus);
        JustDireEarthquakeCompatibility.initialize(eventBus);
        JustDireLavaRepairCompatibility.initialize(eventBus);
        JustDireSettingsCompatibility.initialize(eventBus);
        JustDireHeldToggleCompatibility.initialize(eventBus);
        JustDireActiveNetwork.initialize();
        JustDireAttributesCompatibility.initialize(eventBus);

        NeoVitaeInfusionCompatibility.initialize(eventBus);
        SilentGearCompatibility.initialize(eventBus);
        SilentGearDefense.initialize(eventBus);
        SilentGearEffects.initialize(eventBus);
        SilentGearRecognition.initialize(eventBus);
        SilentGearWearer.initialize(eventBus);
        SilentGearMagnet.initialize(eventBus);
        SilentGearCures.initialize(eventBus);

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            AttributeRulesLifecycle.onServerStarting();
            ComponentRulesLifecycle.onServerStarting();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            JustDireLavaRepairCompatibility.clearEpisodes();
            AttributeRulesLifecycle.onServerStopped();
            ComponentRulesLifecycle.onServerStopped();
        });
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.sendRecipes(MismatchedWorkbench.recipeType()));

        eventBus.addListener(MismatchedBootsClientNeoForge::onClientSetup);
        eventBus.addListener(MismatchedBootsClientNeoForge::registerItemDecorations);
        eventBus.addListener(MismatchedBootsClientNeoForge::registerTooltipFactories);
    }
}
