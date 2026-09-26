package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientForge;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class MismatchedBootsForge {
    public MismatchedBootsForge(FMLJavaModLoadingContext context) {
        ModRegistries.register(context.getModBusGroup());
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(MismatchedBootsForge::onCommonSetup);
        ServerStartingEvent.BUS.addListener(event -> {
            AttributeRulesLifecycle.onServerStarting();
            ComponentRulesLifecycle.onServerStarting();
        });
        ServerStoppedEvent.BUS.addListener(event -> {
            AttributeRulesLifecycle.onServerStopped();
            ComponentRulesLifecycle.onServerStopped();
        });

        if (FMLEnvironment.dist.isClient()) {
            FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(MismatchedBootsClientForge::onClientSetup);
        }
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        MismatchedBoots.init();
    }
}
