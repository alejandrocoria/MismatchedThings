package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.client.network.FabricCompositionRulesClientNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;

public class MismatchedBootsClientFabric implements ClientModInitializer {
    private static boolean recipesSynchronizedSinceVanillaUpdate;

    @Override
    public void onInitializeClient() {
        FabricCompositionRulesClientNetwork.initialize();
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> FabricCompositionRulesClientNetwork.onConnected());
        ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> {
            recipesSynchronizedSinceVanillaUpdate = true;
            MismatchedWorkbenchClientRecipes.install(recipes.getAllOfType(MismatchedWorkbench.recipeType()));
        });
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
        CompositionRulesClientLifecycle.onDisconnected();
        // Called on the client thread after vanilla drops pending tasks. Fabric's
        // Netty disconnect event cannot notify JEI directly or reliably enqueue it.
        recipesSynchronizedSinceVanillaUpdate = false;
        MismatchedWorkbenchClientRecipes.reset();
    }
}
