package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.client.MismatchedLeggingsClientNeoForge;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRulesLifecycle;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedleggings.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsRepairRecipe;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsSmithingBaseRecipe;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LeggingsContent.MOD_ID)
public final class MismatchedLeggingsNeoForge {
    public MismatchedLeggingsNeoForge(IEventBus bus) {
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LeggingsContent.MOD_ID);
        components.register("mixed_leggings_data", LeggingsContent::createDataType);
        components.register(bus);
        var items = DeferredRegister.createItems(LeggingsContent.MOD_ID);
        items.register("mixed_leggings", () -> LeggingsContent.createItem(NeoForgeMixedLeggingsItem::new));
        items.register(bus);
        var serializers = DeferredRegister.create(Registries.RECIPE_SERIALIZER, LeggingsContent.MOD_ID);
        serializers.register("mixed_leggings_workbench", () -> MixedLeggingsWorkbenchRecipe.SERIALIZER);
        serializers.register("mixed_leggings_repair", () -> new MixedLeggingsRepairRecipe(ItemStack::isCombineRepairable).getSerializer());
        serializers.register("mixed_leggings_smithing_base", () -> MixedLeggingsSmithingBaseRecipe.SERIALIZER);
        serializers.register(bus);
        CompositionRulesLifecycle.registerContributor();

        bus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC, (payload, context) ->
                    PacketDistributor.sendToPlayer((ServerPlayer) context.player(), new CompositionRulesPayload(CompositionRuleState.server())));
            registrar.playToClient(CompositionRulesPayload.TYPE, CompositionRulesPayload.STREAM_CODEC,
                    (payload, context) -> CompositionRuleState.installPreview(payload.rules()));
        });
        var configDirectory = FMLPaths.CONFIGDIR.get();
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> CompositionRulesLifecycle.load(configDirectory));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CompositionRulesLifecycle.stopped());
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.sendRecipes(MismatchedWorkbench.recipeType()));

        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(MismatchedLeggingsClientNeoForge::setup);
        }
    }
}
