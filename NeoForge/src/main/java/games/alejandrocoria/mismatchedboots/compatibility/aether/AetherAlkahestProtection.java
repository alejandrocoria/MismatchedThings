package games.alejandrocoria.mismatchedboots.compatibility.aether;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Read-only extensions of the native tier predicates, with independent fluid/armor permissions. */
public final class AetherAlkahestProtection {
    private static final Identifier TIER = Identifier.fromNamespaceAndPath("aether_ii", "reinforcement_tier");
    private static volatile boolean fluidDisabled;
    private static volatile boolean armorDisabled;

    private AetherAlkahestProtection() {
    }

    public static boolean matches(boolean original, ItemStack stack, Supplier<?> supplier, boolean armor) {
        if (original || (armor ? armorDisabled : fluidDisabled) || !(stack.getItem() instanceof MixedBootsItem)) {
            return original;
        }
        try {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(TIER).orElseThrow();
            if (supplier.get() != type) {
                throw new IllegalStateException("Native reinforcement supplier changed");
            }
            return MixedBootsSources.anySource(stack, source -> source.has(type));
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

        String message = "Aether II mixed Alkahest " + (armor ? "armor" : "fluid") + " protection disabled: local contract unavailable";
        if (failure == null) {
            Constants.LOG.warn(message);
        } else {
            Constants.LOG.warn(message, failure);
        }
    }
}
