package games.alejandrocoria.mismatchedboots.platform.services;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.nio.file.Path;

public interface IPlatformHelper {

    Path getConfigDirectory();

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

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

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
