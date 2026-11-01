package games.alejandrocoria.mismatchedleggings.compatibility.jei;

import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchExampleCycle;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.client.viewer.WorkbenchExample;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Optional;

final class MismatchedWorkbenchJeiExamples {
    private MismatchedWorkbenchJeiExamples() {
    }

    static Optional<WorkbenchExample> create(IJeiRuntime runtime) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        var manager = runtime.getIngredientManager();
        var visibility = runtime.getJeiHelpers().getIngredientVisibility();
        var helper = manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        var unique = new LinkedHashMap<Object, ItemStack>();
        for (var stack : manager.getAllItemStacks()) {
            if (visibility.isIngredientVisible(VanillaTypes.ITEM_STACK, stack)) {
                var candidate = stack.copyWithCount(1);
                if (LeggingHalf.isValidSource(candidate)) {
                    unique.putIfAbsent(helper.getUid(candidate, UidContext.Recipe), candidate);
                }
            }
        }
        var candidates = unique.values().stream()
                .sorted(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())).toList();
        return MismatchedWorkbenchExampleCycle.create(candidates, (left, right) ->
                MismatchedWorkbenchClientRecipes.assemblePair(new WorkbenchInput(left, right), level));
    }
}
