package games.alejandrocoria.mismatchedboots.runtimetest.viewer;

import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.network.CompositionRules;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.List;

/** Client-thread delivery control for genuine decoded replies; never supplies synthetic production rules. */
public final class RulesDeliveryGate {
    private static boolean holding;
    private static boolean releasing;
    private static CompositionRules rules;
    private static List<RecipeHolder<WorkbenchRecipe>> recipes;

    private RulesDeliveryGate() {
    }

    public static void begin() {
        rules = null;
        recipes = null;
        holding = true;
    }

    public static boolean holdRules(CompositionRules value) {
        requireClientThread();
        if (!holding || releasing) {
            return false;
        }
        if (rules != null) {
            throw new AssertionError("Duplicate complete rules reply during join");
        }
        rules = value;
        return true;
    }

    public static boolean holdRecipes(Collection<RecipeHolder<WorkbenchRecipe>> value) {
        requireClientThread();
        if (!holding || releasing) {
            return false;
        }
        recipes = List.copyOf(value);
        return true;
    }

    public static boolean captured() {
        return rules != null && recipes != null;
    }

    public static CompositionRules rules() {
        return rules;
    }

    public static void releaseRules() {
        releasing = true;
        try {
            CompositionRulesClientLifecycle.install(rules);
        } finally {
            releasing = false;
        }
    }

    public static void releaseRecipes() {
        releasing = true;
        try {
            MismatchedWorkbenchClientRecipes.install(recipes);
        } finally {
            releasing = false;
        }
    }

    public static void finish() {
        holding = false;
        rules = null;
        recipes = null;
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new AssertionError("Native rules/recipe delivery is not on the client thread");
        }
    }
}
