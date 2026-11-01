package games.alejandrocoria.mismatchedthings.api.client.viewer.jei;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.client.viewer.WorkbenchExample;
import games.alejandrocoria.mismatchedthings.internal.client.viewer.jei.WorkbenchJeiState;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** JEI-only client-thread contribution boundary. Products own generation and invalidation. */
public final class WorkbenchJeiRecipes {
    public static final IRecipeType<WorkbenchExample> TYPE = IRecipeType.create(
            MismatchedWorkbench.ID, WorkbenchExample.class);

    private WorkbenchJeiRecipes() {
    }

    /** Replace only this owner's visible cycle, or remove it when its authority is unavailable. */
    public static void replace(Identifier owner, Optional<WorkbenchExample> example) {
        WorkbenchJeiState.replace(owner, example);
    }
}
