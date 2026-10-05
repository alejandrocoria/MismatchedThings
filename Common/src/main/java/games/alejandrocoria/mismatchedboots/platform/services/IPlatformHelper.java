package games.alejandrocoria.mismatchedboots.platform.services;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.nio.file.Path;

public interface IPlatformHelper {

    Path getConfigDirectory();

    default boolean canCombineRepair(ItemStack stack) {
        return true;
    }

    /**
     * Queries source modifiers for a composition-time snapshot without modifying the source.
     * Loaders with effective attribute hooks must query them on a defensive copy.
     */
    default ItemAttributeModifiers getCompositionAttributeModifiers(ItemStack source) {
        return source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }
}
