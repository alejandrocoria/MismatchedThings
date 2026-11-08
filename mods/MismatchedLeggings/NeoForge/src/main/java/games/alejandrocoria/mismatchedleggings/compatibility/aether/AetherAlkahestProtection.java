package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Read-only native tier extensions; fluid and armor proofs remain independent. */
public final class AetherAlkahestProtection {
    private static final Identifier TIER = Identifier.parse("aether_ii:reinforcement_tier");
    private static volatile boolean fluidDisabled;
    private static volatile boolean armorDisabled;

    private AetherAlkahestProtection() {}

    public static boolean matches(boolean original, ItemStack root, Supplier<?> supplier, boolean armor) {
        if (original || (armor ? armorDisabled : fluidDisabled) || !(root.getItem() instanceof MixedLeggingsItem)) {
            return original;
        }
        try {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(TIER).orElseThrow();
            if (supplier.get() != type) {
                throw new IllegalStateException("Native reinforcement supplier changed");
            }
            return AetherSources.any(root, source -> source.has(type));
        } catch (RuntimeException | LinkageError failure) {
            disable(armor, failure);
            return original;
        }
    }

    static synchronized void disable(boolean armor, Throwable failure) {
        if (armor ? armorDisabled : fluidDisabled) {
            return;
        }
        if (armor) {
            armorDisabled = true;
        } else {
            fluidDisabled = true;
        }
        String message = "Aether II mixed leggings Alkahest " + (armor ? "armor" : "fluid") + " disabled: local tier contract unavailable";
        if (failure == null) {
            LeggingsContent.LOG.warn(message);
        } else {
            LeggingsContent.LOG.warn(message, failure);
        }
    }
}
