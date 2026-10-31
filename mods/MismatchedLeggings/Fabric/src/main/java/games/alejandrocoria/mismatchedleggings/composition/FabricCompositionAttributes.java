package games.alejandrocoria.mismatchedleggings.composition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class FabricCompositionAttributes implements CompositionAttributes {
    @Override
    public ItemAttributeModifiers query(ItemStack source) {
        return source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }
}
