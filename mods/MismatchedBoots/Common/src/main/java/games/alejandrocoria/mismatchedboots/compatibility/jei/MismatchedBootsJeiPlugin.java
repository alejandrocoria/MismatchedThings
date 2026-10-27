package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JeiPlugin
public final class MismatchedBootsJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "jei");

    private @Nullable IJeiRuntime runtime;
    private Runnable removeRulesListener = () -> {
    };
    private Runnable removeRecipesListener = () -> {
    };
    private @Nullable MismatchedWorkbenchJeiExample registeredExample;

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MismatchedWorkbenchJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
                MismatchedWorkbenchJeiCategory.TYPE,
                MismatchedWorkbench.item());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        removeRulesListener.run();
        removeRecipesListener.run();
        runtime = jeiRuntime;
        registeredExample = null;
        removeRecipesListener = MismatchedWorkbenchClientRecipes.addListener(this::refreshExample);
        removeRulesListener = CompositionRulesClientLifecycle.addSynchronizedListener(this::refreshExample);
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
        registeredExample = null;
    }

    private void refreshExample() {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            return;
        }
        if (registeredExample != null) {
            currentRuntime.getRecipeManager().hideRecipes(
                    MismatchedWorkbenchJeiCategory.TYPE, List.of(registeredExample));
            registeredExample = null;
        }
        if (!CompositionRulesClientLifecycle.areRulesSynchronized()) {
            return;
        }

        MismatchedWorkbenchJeiExamples.create(currentRuntime).ifPresent(example -> {
            currentRuntime.getRecipeManager().addRecipes(
                    MismatchedWorkbenchJeiCategory.TYPE,
                    List.of(example));
            registeredExample = example;
        });
    }
}
