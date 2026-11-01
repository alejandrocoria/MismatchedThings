package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedleggings.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;

public final class MismatchedLeggingsClientFabric implements ClientModInitializer {
    private static boolean recipesSynchronizedSinceVanillaUpdate;
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CompositionRulesPayload.TYPE,
                (payload, context) -> CompositionRuleState.installPreview(payload.rules()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CompositionRuleState.resetPreview();
            sender.sendPacket(new ClientReadyPayload());
        });
        ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> {
            recipesSynchronizedSinceVanillaUpdate = true;
            MismatchedWorkbenchClientRecipes.install(recipes.getAllOfType(MismatchedWorkbench.recipeType()));
        });
    }

    public static void onVanillaRecipesUpdated() {
        // Fabric API sends custom recipes before vanilla's update, but omits an entirely empty sync.
        // As in Boots, consume this update's marker so removing the last recipe clears old examples.
        if (!recipesSynchronizedSinceVanillaUpdate) {
            MismatchedWorkbenchClientRecipes.reset();
        }
        recipesSynchronizedSinceVanillaUpdate = false;
    }

    public static void onClientDisconnected() {
        // Run after vanilla drops pending tasks, on the client thread; Netty's event cannot notify JEI safely.
        recipesSynchronizedSinceVanillaUpdate = false;
        CompositionRuleState.resetPreview();
        MismatchedWorkbenchClientRecipes.reset();
    }
}
