package games.alejandrocoria.mismatchedthings.spike.core;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record WorkbenchResult(ItemStack primary, ItemStack inverse) {
    public static final WorkbenchResult EMPTY =
            new WorkbenchResult(ItemStack.EMPTY, ItemStack.EMPTY);

    public WorkbenchResult {
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
