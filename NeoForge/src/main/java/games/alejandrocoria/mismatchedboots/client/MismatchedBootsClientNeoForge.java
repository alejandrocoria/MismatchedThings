package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireAbilityInput;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class MismatchedBootsClientNeoForge {
    private MismatchedBootsClientNeoForge() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        JustDireActiveNetwork.clientReplies(JustDireAbilityInput::reply, JustDireSettingsClient::reply);
        NeoForge.EVENT_BUS.addListener((InputEvent.Key key) -> JustDireAbilityInput.key(key));
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Post mouse) -> JustDireAbilityInput.mouse(mouse));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> {
            JustDireSettingsClient.tick(); JustDireAbilityInput.tick();
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
            JustDireLavaRepairCompatibility.clearClientEpisodes();
            JustDireSettingsClient.reset(); JustDireAbilityInput.reset();
            CompositionRulesClientLifecycle.onDisconnected();
            MismatchedWorkbenchClientRecipes.reset();
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload unload) -> {
            if (unload.getLevel().isClientSide()) { JustDireLavaRepairCompatibility.clearClientEpisodes(); }
        });
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.mismatchedWorkbenchMenuType().orElseThrow(),
                MismatchedWorkbenchScreen::new);
    }
}
