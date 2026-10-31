package games.alejandrocoria.mismatchedleggings.composition;

import net.minecraft.world.item.ItemStack;

/** Independently owned mutable outputs, never aliased to their inputs or one another. */
public record MixedLeggingsPair(ItemStack primary, ItemStack inverse) {
}
