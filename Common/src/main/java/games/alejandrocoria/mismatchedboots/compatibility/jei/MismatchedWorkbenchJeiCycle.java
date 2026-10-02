package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

final class MismatchedWorkbenchJeiCycle {
    private MismatchedWorkbenchJeiCycle() {
    }

    static List<ItemStack> collectCandidates(
            Iterable<ItemStack> ingredients,
            Predicate<ItemStack> isVisible,
            Function<ItemStack, Object> recipeUid,
            Function<ItemStack, String> sortKey) {
        Map<Object, ItemStack> uniqueBoots = new LinkedHashMap<>();
        for (ItemStack stack : ingredients) {
            if (!isVisible.test(stack)) {
                continue;
            }
            ItemStack candidate = stack.copyWithCount(1);
            if (BootHalf.isValidSource(candidate)) {
                uniqueBoots.putIfAbsent(recipeUid.apply(candidate), candidate);
            }
        }

        List<ItemStack> boots = new ArrayList<>(uniqueBoots.values());
        boots.sort(Comparator.comparing(sortKey));
        return List.copyOf(boots);
    }

    static Optional<MismatchedWorkbenchJeiExample> create(
            List<ItemStack> boots,
            BiFunction<ItemStack, ItemStack, MismatchedWorkbenchResult> composer) {
        if (boots.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> leftInputs = new ArrayList<>(boots.size());
        List<ItemStack> rightInputs = new ArrayList<>(boots.size());
        List<ItemStack> primaryOutputs = new ArrayList<>(boots.size());
        List<ItemStack> inverseOutputs = new ArrayList<>(boots.size());

        for (int index = 0; index < boots.size(); index++) {
            ItemStack left = boots.get(index);
            ItemStack right = boots.get((index + 1) % boots.size());
            MismatchedWorkbenchResult result = composer.apply(left, right);
            if (!result.isComplete()) {
                continue;
            }

            leftInputs.add(left);
            rightInputs.add(right);
            primaryOutputs.add(result.primary());
            inverseOutputs.add(result.inverse());
        }

        if (leftInputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MismatchedWorkbenchJeiExample(
                leftInputs,
                rightInputs,
                primaryOutputs,
                inverseOutputs));
    }
}
