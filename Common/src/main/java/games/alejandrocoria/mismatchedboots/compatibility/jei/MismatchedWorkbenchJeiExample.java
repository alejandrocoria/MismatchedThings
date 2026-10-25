package games.alejandrocoria.mismatchedboots.compatibility.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

record MismatchedWorkbenchJeiExample(
        List<ItemStack> leftInputs,
        List<ItemStack> rightInputs,
        List<ItemStack> primaryOutputs,
        List<ItemStack> inverseOutputs) {
    MismatchedWorkbenchJeiExample {
        leftInputs = copyStacks(leftInputs, "leftInputs");
        rightInputs = copyStacks(rightInputs, "rightInputs");
        primaryOutputs = copyStacks(primaryOutputs, "primaryOutputs");
        inverseOutputs = copyStacks(inverseOutputs, "inverseOutputs");

        int size = leftInputs.size();
        if (rightInputs.size() != size || primaryOutputs.size() != size || inverseOutputs.size() != size) {
            throw new IllegalArgumentException("linked JEI slots must contain the same number of stacks");
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
