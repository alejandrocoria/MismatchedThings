package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireGroundStompInput;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MismatchedBootsClientNeoForge {
    private MismatchedBootsClientNeoForge() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        JustDireActiveNetwork.clientReplies(JustDireGroundStompInput::reply, JustDireSettingsClient::reply);
        NeoForge.EVENT_BUS.addListener((InputEvent.Key key) -> JustDireGroundStompInput.key(key));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> {
            JustDireSettingsClient.tick(); JustDireGroundStompInput.tick();
        });
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
            JustDireSettingsClient.reset(); JustDireGroundStompInput.reset();
            CompositionRulesClientLifecycle.onDisconnected();
            MismatchedWorkbenchClientRecipes.reset();
        });
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.mismatchedWorkbenchMenuType().orElseThrow(),
                MismatchedWorkbenchScreen::new);
    }
}
