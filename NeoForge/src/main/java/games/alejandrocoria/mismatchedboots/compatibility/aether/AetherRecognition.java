package games.alejandrocoria.mismatchedboots.compatibility.aether;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Read-only local query extensions. Native callers still own counts, effects and fall conditions. */
public final class AetherRecognition {
    private static final Identifier ARMOR_SET = Identifier.fromNamespaceAndPath("aether_ii", "armor_set");
    private static final ResourceKey<Item> SENTRY = ResourceKey.create(Registries.ITEM,
                                                                       Identifier.fromNamespaceAndPath("aether_ii", "sentry_boots"));
    private static volatile boolean setsDisabled, sentryDisabled;

    private AetherRecognition() {
    }

    public static boolean matchesSet(Object original, TagKey<Item> requested, ItemStack root) {
        if (original == requested) {
            return true;
        }
        if (setsDisabled || requested == null || !requested.registry().equals(Registries.ITEM)) {
            return false;
        }
        try {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ARMOR_SET).orElseThrow();
            // One boolean for this worn piece, even if both histories carry the same set.
            return MixedBootsSources.anySource(root, source -> source.get(type) == requested);
        } catch (RuntimeException | LinkageError failure) {
            disable(true, failure);
            return false;
        }
    }

    public static boolean matchesSentry(boolean original, ItemStack root) {
        if (original || sentryDisabled) {
            return original;
        }
        try {
            return MixedBootsSources.anySource(root, source -> source.is(SENTRY));
        } catch (RuntimeException | LinkageError failure) {
            disable(false, failure);
            return original;
        }
    }

    static synchronized void disable(boolean sets, Throwable failure) {
        if (sets ? setsDisabled : sentryDisabled) {
            return;
        }
        if (sets) {
            setsDisabled = true;
        } else {
            sentryDisabled = true;
        }
        String message = "Aether II mixed " + (sets ? "set" : "Sentry") + " recognition disabled: local contract unavailable";
        if (failure == null) {
            Constants.LOG.warn(message);
        } else {
            Constants.LOG.warn(message, failure);
        }
    }
}
