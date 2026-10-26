package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public final class MismatchedWorkbenchExampleCycle {
    private MismatchedWorkbenchExampleCycle() {
    }

    public static Optional<Example> create(
            List<ItemStack> candidates,
            BiFunction<ItemStack, ItemStack, MismatchedWorkbenchResult> composer) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(composer, "composer");
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> leftInputs = new ArrayList<>(candidates.size());
        List<ItemStack> rightInputs = new ArrayList<>(candidates.size());
        List<ItemStack> primaryOutputs = new ArrayList<>(candidates.size());
        List<ItemStack> inverseOutputs = new ArrayList<>(candidates.size());

        for (int index = 0; index < candidates.size(); index++) {
            ItemStack left = candidates.get(index);
            ItemStack right = candidates.get((index + 1) % candidates.size());
            MismatchedWorkbenchResult result = composer.apply(left, right);
            if (!result.isComplete()) {
                continue;
            }

            leftInputs.add(left);
            rightInputs.add(right);
            primaryOutputs.add(result.primary());
            inverseOutputs.add(result.inverse());
        }

        return leftInputs.isEmpty()
               ? Optional.empty()
               : Optional.of(new Example(leftInputs, rightInputs, primaryOutputs, inverseOutputs));
    }

    public record Example(
            List<ItemStack> leftInputs,
            List<ItemStack> rightInputs,
            List<ItemStack> primaryOutputs,
            List<ItemStack> inverseOutputs) {
        public Example {
            leftInputs = copyStacks(leftInputs, "leftInputs");
            rightInputs = copyStacks(rightInputs, "rightInputs");
            primaryOutputs = copyStacks(primaryOutputs, "primaryOutputs");
            inverseOutputs = copyStacks(inverseOutputs, "inverseOutputs");

            int size = leftInputs.size();
            if (rightInputs.size() != size || primaryOutputs.size() != size || inverseOutputs.size() != size) {
                throw new IllegalArgumentException("linked example slots must contain the same number of stacks");
            }
        }

        @Override
        public List<ItemStack> leftInputs() {
            return copyStacks(leftInputs, "leftInputs");
        }

        @Override
        public List<ItemStack> rightInputs() {
            return copyStacks(rightInputs, "rightInputs");
        }

        @Override
        public List<ItemStack> primaryOutputs() {
            return copyStacks(primaryOutputs, "primaryOutputs");
        }

        @Override
        public List<ItemStack> inverseOutputs() {
            return copyStacks(inverseOutputs, "inverseOutputs");
        }

        private static List<ItemStack> copyStacks(List<ItemStack> stacks, String name) {
            Objects.requireNonNull(stacks, name);
            if (stacks.isEmpty()) {
                throw new IllegalArgumentException(name + " cannot be empty");
            }
            return stacks.stream()
                         .map(stack -> copyStack(stack, name))
                         .toList();
        }

        private static ItemStack copyStack(ItemStack stack, String listName) {
            Objects.requireNonNull(stack, listName + " element");
            if (stack.isEmpty()) {
                throw new IllegalArgumentException(listName + " cannot contain an empty stack");
            }
            return stack.copyWithCount(1);
        }
    }
}
