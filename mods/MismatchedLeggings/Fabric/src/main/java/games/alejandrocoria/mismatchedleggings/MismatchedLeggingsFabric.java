package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRulesLifecycle;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedleggings.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MismatchedLeggingsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, LeggingsContent.MIXED_LEGGINGS_DATA_ID, LeggingsContent.createDataType());
        Registry.register(BuiltInRegistries.ITEM, LeggingsContent.MIXED_LEGGINGS_ID, LeggingsContent.createItem());
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MixedLeggingsWorkbenchRecipe.ID, MixedLeggingsWorkbenchRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(MixedLeggingsWorkbenchRecipe.SERIALIZER);
        CompositionRulesLifecycle.registerContributor();

        PayloadTypeRegistry.clientboundPlay().register(CompositionRulesPayload.TYPE, CompositionRulesPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(), new CompositionRulesPayload(CompositionRuleState.server())));

        var configDirectory = FabricLoader.getInstance().getConfigDir();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CompositionRulesLifecycle.load(configDirectory));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CompositionRulesLifecycle.stopped());
    }
}
