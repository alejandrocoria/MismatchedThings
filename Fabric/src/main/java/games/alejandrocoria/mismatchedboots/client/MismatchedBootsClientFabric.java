package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.minecraft.client.gui.screens.MenuScreens;

public class MismatchedBootsClientFabric implements ClientModInitializer {
    private static boolean recipesSynchronizedSinceVanillaUpdate;

    @Override
    public void onInitializeClient() {
        MismatchedBootsClient.init();
        MenuScreens.register(ModContent.mismatchedWorkbenchMenuType().orElseThrow(),
                MismatchedWorkbenchScreen::new);
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> CompositionRulesClientLifecycle.onConnected());
        ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> {
            recipesSynchronizedSinceVanillaUpdate = true;
            MismatchedWorkbenchClientRecipes.install(recipes.getAllOfType(ModContent.mismatchedWorkbenchRecipeType()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> CompositionRulesClientLifecycle.onDisconnected());
    }

    public static void onVanillaRecipesUpdated() {
        // Fabric API 9.0.16's PlayerList lifecycle hooks send recipe sync before
        // vanilla's update on login/reload, but omit an entirely empty sync.
        // Consume this update's fresh-snapshot marker, or invalidate the old state.
        // Recheck that upstream packet ordering when porting this loader adapter.
        if (!recipesSynchronizedSinceVanillaUpdate) {
            MismatchedWorkbenchClientRecipes.reset();
        }
        recipesSynchronizedSinceVanillaUpdate = false;
    }

    public static void onClientDisconnected() {
        // Called on the client thread after vanilla drops pending tasks. Fabric's
        // Netty disconnect event cannot notify JEI directly or reliably enqueue it.
        recipesSynchronizedSinceVanillaUpdate = false;
        MismatchedWorkbenchClientRecipes.reset();
    }
}
