package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class MismatchedBootsForge {
    public MismatchedBootsForge(FMLJavaModLoadingContext context) {
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(MismatchedBootsForge::onCommonSetup);

        if (FMLEnvironment.dist.isClient()) {
            FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(MismatchedBootsClientForge::onClientSetup);
        }
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        MismatchedBoots.init();
    }
}
