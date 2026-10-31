package games.alejandrocoria.mismatchedleggings.composition;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Composition-time loader boundary, never a per-tick source activation bridge. */
public interface CompositionAttributes {
    ItemAttributeModifiers query(ItemStack source);
}
