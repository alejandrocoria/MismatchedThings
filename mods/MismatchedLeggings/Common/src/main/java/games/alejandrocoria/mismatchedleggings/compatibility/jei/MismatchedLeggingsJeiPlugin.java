package games.alejandrocoria.mismatchedleggings.compatibility.jei;

import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedthings.api.client.viewer.jei.WorkbenchJeiRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public final class MismatchedLeggingsJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "jei");

    private @Nullable IJeiRuntime runtime;
    private Runnable removeRulesListener = () -> {
    };
    private Runnable removeRecipesListener = () -> {
    };

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        removeRulesListener.run();
        removeRecipesListener.run();
        runtime = jeiRuntime;
        removeRecipesListener = MismatchedWorkbenchClientRecipes.addListener(this::refreshExample);
        removeRulesListener = CompositionRuleState.addPreviewListener(this::refreshExample);
    }

    @Override
    public void onRuntimeUnavailable() {
        removeRulesListener.run();
        removeRecipesListener.run();
        removeRulesListener = () -> {
        };
        removeRecipesListener = () -> {
        };
        runtime = null;
        WorkbenchJeiRecipes.replace(PLUGIN_ID, Optional.empty());
    }

    private void refreshExample() {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            return;
        }
        if (CompositionRuleState.synchronizedPreview() == null) {
            WorkbenchJeiRecipes.replace(PLUGIN_ID, Optional.empty());
            return;
        }

        WorkbenchJeiRecipes.replace(PLUGIN_ID, MismatchedWorkbenchJeiExamples.create(currentRuntime));
    }
}
