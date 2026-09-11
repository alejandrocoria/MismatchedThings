package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record MixedBootsPair(ItemStack primary, ItemStack inverse) {
    public MixedBootsPair {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(inverse, "inverse");
    }
}
