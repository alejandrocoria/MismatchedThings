package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Client-only recipe authority supplied by each loader's existing recipe synchronization. */
public final class MismatchedWorkbenchClientRecipes {
    private static List<RecipeHolder<MismatchedWorkbenchRecipe>> recipes = List.of();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private MismatchedWorkbenchClientRecipes() {
    }

    // Install/reset and viewer callbacks run on the client thread, including integrated play.
    public static void install(Collection<RecipeHolder<MismatchedWorkbenchRecipe>> loaded) {
        recipes = List.copyOf(loaded);
        notifyListeners();
    }

    public static void reset() {
        recipes = List.of();
        notifyListeners();
    }

    public static boolean hasRecipes() {
        return !recipes.isEmpty();
    }

    public static MismatchedWorkbenchResult assemblePair(
            MismatchedWorkbenchInput input, Level level, AttributeRuleSet attributes, ComponentRules components) {
        return input.isEmpty() ? MismatchedWorkbenchResult.EMPTY : recipes.stream()
                .filter(holder -> holder.value().matches(input, level))
                .findFirst()
                .map(holder -> holder.value().assemblePreview(input, attributes, components))
                .orElse(MismatchedWorkbenchResult.EMPTY);
    }

    public static Runnable addListener(Runnable listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
        return () -> LISTENERS.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : List.copyOf(LISTENERS)) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                Constants.LOG.error("Workbench client-recipe listener failed", exception);
            }
        }
    }
}
