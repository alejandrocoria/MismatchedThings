package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ServiceLoader;

/** Explicit output lifecycle seam; attribute queries never initialize live state. */
public interface SilentGearComposition {
    SilentGearComposition INSTANCE = ServiceLoader.load(SilentGearComposition.class).findFirst()
            .orElse((previous, output, data, rules) -> {});

    /** Fresh unpublished output, with final durability and attributes, before structural installation. */
    void prepare(@Nullable ItemStack previous, ItemStack output, MixedLeggingsData data, CompositionRules rules);
}
