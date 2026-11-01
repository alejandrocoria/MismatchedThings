package games.alejandrocoria.mismatchedthings.internal.client.viewer.jei;

import games.alejandrocoria.mismatchedthings.api.client.viewer.WorkbenchExample;
import games.alejandrocoria.mismatchedthings.api.client.viewer.jei.WorkbenchJeiRecipes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One client-thread registry per JEI runtime, independent of product initialization order. */
public final class WorkbenchJeiState {
    private static final Map<Identifier, WorkbenchExample> EXAMPLES = new LinkedHashMap<>();
    private static @Nullable IJeiRuntime runtime;

    private WorkbenchJeiState() {
    }

    public static void replace(Identifier owner, Optional<WorkbenchExample> replacement) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(replacement, "replacement");
        WorkbenchExample previous = EXAMPLES.remove(owner);
        if (runtime != null && previous != null) {
            runtime.getRecipeManager().hideRecipes(WorkbenchJeiRecipes.TYPE, List.of(previous));
        }

        replacement.ifPresent(example -> {
            EXAMPLES.put(owner, example);
            if (runtime != null) {
                runtime.getRecipeManager().addRecipes(WorkbenchJeiRecipes.TYPE, List.of(example));
            }
        });
    }

    static void available(IJeiRuntime current) {
        runtime = current;
        // Product callbacks can run before or after Things' callback. Retain their pending values.
        if (!EXAMPLES.isEmpty()) {
            current.getRecipeManager().addRecipes(WorkbenchJeiRecipes.TYPE, List.copyOf(EXAMPLES.values()));
        }
    }

    static void unavailable() {
        runtime = null;
        EXAMPLES.clear();
    }
}
