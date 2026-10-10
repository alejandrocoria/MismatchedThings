package games.alejandrocoria.mismatchedboots.platform.services;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
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

    /** Finalizes only the new root, after generic composition and before strict validation. */
    default void finalizeMixedBootsCreation(ItemStack output, AttributeRuleSet attributes) {}

    /** Old live state is read-only; resource preservation must not use source snapshots as wallets. */
    default void finalizeMixedBootsRecomposition(ItemStack previous, ItemStack output, AttributeRuleSet attributes) {}

    /** A handled rejection must not fall through to generic source-wise installation. */
    default MixedBootsSmithingPolicy.RootAttempt tryRootSmithing(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        return MixedBootsSmithingPolicy.RootAttempt.UNHANDLED;
    }
}
