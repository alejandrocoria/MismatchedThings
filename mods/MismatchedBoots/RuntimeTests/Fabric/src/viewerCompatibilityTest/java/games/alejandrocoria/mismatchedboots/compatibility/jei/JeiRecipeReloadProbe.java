package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.runtimetest.viewer.RecipeViewerReloadClientGameTest;
import games.alejandrocoria.mismatchedthings.api.client.viewer.jei.WorkbenchJeiRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

@JeiPlugin
public final class JeiRecipeReloadProbe implements IModPlugin, RecipeViewerReloadClientGameTest.Probe {
    private static IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.parse("mismatchedboots-viewer-test:reload_probe");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime current) {
        requireClientThread();
        runtime = current;
    }

    @Override
    public void onRuntimeUnavailable() {
        requireClientThread();
        runtime = null;
    }

    @Override
    public boolean ready() {
        return runtime != null;
    }

    @Override
    public List<RecipeViewerReloadClientGameTest.Cycle> cycles() {
        return runtime.getRecipeManager().createRecipeLookup(WorkbenchJeiRecipes.TYPE).get()
                      .map(example -> new RecipeViewerReloadClientGameTest.Cycle(example.leftInputs(), example.rightInputs(),
                                                                                 example.primaryOutputs(), example.inverseOutputs())).toList();
    }

    @Override
    public void open() {
        runtime.getRecipesGui().showTypes(List.of(WorkbenchJeiRecipes.TYPE));
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new AssertionError("JEI runtime lifecycle must notify product subscribers on the client thread");
        }
    }
}
