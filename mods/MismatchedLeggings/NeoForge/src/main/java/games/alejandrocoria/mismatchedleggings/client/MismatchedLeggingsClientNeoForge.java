package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.client.compatibility.forbiddenarcanus.ForbiddenModifierTooltip;
import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

public final class MismatchedLeggingsClientNeoForge {
    private MismatchedLeggingsClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
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
                CompositionRuleState.resetPreview();
                MismatchedWorkbenchClientRecipes.reset();
            });
        });
    }
}
