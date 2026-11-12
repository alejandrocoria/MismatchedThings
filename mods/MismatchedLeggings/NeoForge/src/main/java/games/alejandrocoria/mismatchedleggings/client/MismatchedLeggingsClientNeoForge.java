package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.client.compatibility.aether.AetherSourceCharms;
import games.alejandrocoria.mismatchedleggings.client.compatibility.aether.AetherTooltipClient;
import games.alejandrocoria.mismatchedleggings.client.compatibility.forbiddenarcanus.ForbiddenModifierTooltip;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireEnergyBarDecorator;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireInputClient;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

public final class MismatchedLeggingsClientNeoForge {
    private MismatchedLeggingsClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            JustDireSettingsNetwork.clientReplies(JustDireSettingsClient::reply);
            JustDireInputNetwork.clientReplies(JustDireInputClient::reply);
            NeoForge.EVENT_BUS.addListener(JustDireInputClient::key);
            NeoForge.EVENT_BUS.addListener(JustDireInputClient::mouse);
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> JustDireInputClient.tick());
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> JustDireSettingsClient.tick());
            if (ModList.get().isLoaded("aether_ii")) {
                NeoForge.EVENT_BUS.addListener(AetherSourceCharms::gather);
                NeoForge.EVENT_BUS.addListener(AetherTooltipClient::attributes);
            }
            if (ModList.get().isLoaded("forbidden_arcanus")) {
                NeoForge.EVENT_BUS.addListener(ForbiddenModifierTooltip::title);
            }
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn login) -> {
                CompositionRuleState.resetPreview();
                ClientPacketDistributor.sendToServer(new ClientReadyPayload());
            });
            NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent recipes) -> {
                if (recipes.getRecipeTypes().contains(MismatchedWorkbench.recipeType())) {
                    MismatchedWorkbenchClientRecipes.install(recipes.getRecipeMap().byType(MismatchedWorkbench.recipeType()));
                }
            });
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut logout) -> {
                JustDireSettingsClient.reset();
                JustDireInputClient.reset();
                CompositionRuleState.resetPreview();
                MismatchedWorkbenchClientRecipes.reset();
            });
        });
    }

    public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        if (ModList.get().isLoaded("aether_ii")) {
            AetherSourceCharms.registerFactory(event);
        }
    }

    public static void registerDecorators(RegisterItemDecorationsEvent event) {
        if (ModList.get().isLoaded("justdirethings")) {
            event.register(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID), JustDireEnergyBarDecorator.INSTANCE);
        }
    }
}
