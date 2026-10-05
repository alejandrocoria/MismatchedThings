package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public class MismatchedBootsClientNeoForge {
    public MismatchedBootsClientNeoForge() {
    }

    public static void onClientSetup(FMLClientSetupEvent event, IEventBus eventBus) {
        MismatchedBootsClient.init();
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn login) -> CompositionRulesClientLifecycle.onConnected());
        NeoForge.EVENT_BUS.addListener(
                (RecipesReceivedEvent recipes) -> {
                    if (recipes.getRecipeTypes().contains(ModContent.mismatchedWorkbenchRecipeType())) {
                        MismatchedWorkbenchClientRecipes.install(
                                recipes.getRecipeMap().byType(ModContent.mismatchedWorkbenchRecipeType()));
                    }
                });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut logout) -> {
            CompositionRulesClientLifecycle.onDisconnected();
            MismatchedWorkbenchClientRecipes.reset();
        });
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.mismatchedWorkbenchMenuType().orElseThrow(),
                MismatchedWorkbenchScreen::new);
    }
}
