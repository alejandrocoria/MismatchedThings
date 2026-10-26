package games.alejandrocoria.mismatchedboots.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.Objects;

public record MismatchedWorkbenchInput(ItemStack left, ItemStack right) implements RecipeInput {
    public MismatchedWorkbenchInput {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> left;
            case 1 -> right;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
