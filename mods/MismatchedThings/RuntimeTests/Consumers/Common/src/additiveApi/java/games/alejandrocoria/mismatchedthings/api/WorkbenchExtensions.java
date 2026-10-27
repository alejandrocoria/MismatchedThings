package games.alejandrocoria.mismatchedthings.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Test-only additive API, packaged in the candidate core, never in the released core or consumer. */
public final class WorkbenchExtensions {
    private WorkbenchExtensions() {
    }

    public static void registerExtendedContributor(Identifier owner, Predicate<ItemStack> accepts) {
        MismatchedWorkbench.registerContributor(owner, accepts);
    }
}
