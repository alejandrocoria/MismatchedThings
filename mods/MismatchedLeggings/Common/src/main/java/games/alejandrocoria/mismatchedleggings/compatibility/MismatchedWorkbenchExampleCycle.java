package games.alejandrocoria.mismatchedleggings.compatibility;

import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import games.alejandrocoria.mismatchedthings.api.client.viewer.WorkbenchExample;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public final class MismatchedWorkbenchExampleCycle {
    private MismatchedWorkbenchExampleCycle() {
    }

    public static Optional<WorkbenchExample> create(
            List<ItemStack> candidates,
            BiFunction<ItemStack, ItemStack, WorkbenchResult> composer) {
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
            WorkbenchResult result = composer.apply(left, right);
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
               : Optional.of(new WorkbenchExample(leftInputs, rightInputs, primaryOutputs, inverseOutputs));
    }

}
