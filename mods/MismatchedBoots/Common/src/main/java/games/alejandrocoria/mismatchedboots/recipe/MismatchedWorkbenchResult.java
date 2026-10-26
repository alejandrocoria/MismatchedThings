package games.alejandrocoria.mismatchedboots.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record MismatchedWorkbenchResult(ItemStack primary, ItemStack inverse) {
    public static final MismatchedWorkbenchResult EMPTY =
            new MismatchedWorkbenchResult(ItemStack.EMPTY, ItemStack.EMPTY);

    public MismatchedWorkbenchResult {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(inverse, "inverse");
        primary = primary.copy();
        inverse = inverse.copy();
    }

    @Override
    public ItemStack primary() {
        return primary.copy();
    }

    @Override
    public ItemStack inverse() {
        return inverse.copy();
    }

    public boolean isComplete() {
        return !primary.isEmpty() && !inverse.isEmpty();
    }
}
