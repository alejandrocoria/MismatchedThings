package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Same packaged harness, deliberately without the Leggings product or its runtime authority. */
public final class BootsOnlyViewerChecks {
    private static boolean serverFinished;

    private BootsOnlyViewerChecks() {
    }

    public static void environment(boolean boots, boolean leggings, long cores) {
        require(boots && !leggings && cores == 1, "Boots-only viewer graph contains Leggings or duplicate cores");
    }

    public static void serverTick(MinecraftServer server) {
        if (!server.isDedicatedServer() || serverFinished || server.getTickCount() < 20) {
            return;
        }
        serverFinished = true;
        try {
            var level = server.overworld();
            var boots = new WorkbenchInput(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
            var recipe = level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), boots, level).orElseThrow();
            require(recipe.value().assemblePair(boots).isComplete(), "Boots-only workbench recipe is absent");
            require(level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(),
                    new WorkbenchInput(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)), level).isEmpty(),
                    "Uninstalled Leggings contributed a recipe");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

}
