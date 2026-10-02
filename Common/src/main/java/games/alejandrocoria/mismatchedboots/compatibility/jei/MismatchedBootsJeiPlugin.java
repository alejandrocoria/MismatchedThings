package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
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
    private boolean exampleRegistered;

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
                ModContent.mismatchedWorkbenchItem().orElseThrow());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        removeRulesListener.run();
        runtime = jeiRuntime;
        exampleRegistered = false;
        removeRulesListener = CompositionRulesClientLifecycle.addSynchronizedListener(this::registerExample);
    }

    @Override
    public void onRuntimeUnavailable() {
        removeRulesListener.run();
        removeRulesListener = () -> {
        };
        runtime = null;
        exampleRegistered = false;
    }

    private void registerExample() {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null
                || exampleRegistered
                || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
            return;
        }

        MismatchedWorkbenchJeiExamples.create(currentRuntime).ifPresent(example -> {
            currentRuntime.getRecipeManager().addRecipes(
                    MismatchedWorkbenchJeiCategory.TYPE,
                    List.of(example));
            exampleRegistered = true;
        });
    }
}
