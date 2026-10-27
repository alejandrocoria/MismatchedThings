package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchExampleCycle;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
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
            BiFunction<ItemStack, ItemStack, WorkbenchResult> composer) {
        return MismatchedWorkbenchExampleCycle.create(boots, composer)
                                              .map(example -> new MismatchedWorkbenchJeiExample(
                                                      example.leftInputs(),
                                                      example.rightInputs(),
                                                      example.primaryOutputs(),
                                                      example.inverseOutputs()));
    }
}
