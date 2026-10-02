package games.alejandrocoria.mismatchedboots.compatibility.rei;

import games.alejandrocoria.mismatchedboots.Constants;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class MismatchedWorkbenchReiDisplay extends BasicDisplay {
    static final CategoryIdentifier<MismatchedWorkbenchReiDisplay> CATEGORY =
            CategoryIdentifier.of(Constants.MOD_ID, "mismatched_workbench");

    private MismatchedWorkbenchReiDisplay(
            EntryIngredient leftInput,
            EntryIngredient rightInput,
            EntryIngredient primaryOutput,
            EntryIngredient inverseOutput) {
        super(List.of(leftInput, rightInput), List.of(primaryOutput, inverseOutput), Optional.empty());
    }

    static MismatchedWorkbenchReiDisplay create(
            List<ItemStack> leftInputs,
            List<ItemStack> rightInputs,
            List<ItemStack> primaryOutputs,
            List<ItemStack> inverseOutputs) {
        int size = leftInputs.size();
        if (size == 0
                || rightInputs.size() != size
                || primaryOutputs.size() != size
                || inverseOutputs.size() != size) {
            throw new IllegalArgumentException("linked REI slots must contain the same non-zero number of stacks");
        }
        return new MismatchedWorkbenchReiDisplay(
                ingredient(leftInputs),
                ingredient(rightInputs),
                ingredient(primaryOutputs),
                ingredient(inverseOutputs));
    }

    private static EntryIngredient ingredient(List<ItemStack> stacks) {
        return EntryIngredient.of(stacks.stream()
                .map(stack -> Objects.requireNonNull(stack, "stack").copyWithCount(1))
                .map(EntryStacks::of)
                .toList());
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CATEGORY;
    }

    @Override
    public @Nullable DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
}
