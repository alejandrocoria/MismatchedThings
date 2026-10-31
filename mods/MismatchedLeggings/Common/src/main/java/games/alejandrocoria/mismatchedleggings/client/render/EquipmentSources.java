package games.alejandrocoria.mismatchedleggings.client.render;

import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

/** Loader boundary for renderers that bypass the native equipment submission path. */
public interface EquipmentSources {
    EquipmentSources INSTANCE = ServiceLoader.load(EquipmentSources.class).findFirst().orElseThrow();

    boolean usesStandardRenderer(ItemStack source);
}
