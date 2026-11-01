package games.alejandrocoria.mismatchedthings.internal.client.viewer.jei;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.client.viewer.jei.WorkbenchJeiRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class WorkbenchJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(MismatchedWorkbench.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new WorkbenchJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(WorkbenchJeiRecipes.TYPE, MismatchedWorkbench.item());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        WorkbenchJeiState.available(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        WorkbenchJeiState.unavailable();
    }
}
