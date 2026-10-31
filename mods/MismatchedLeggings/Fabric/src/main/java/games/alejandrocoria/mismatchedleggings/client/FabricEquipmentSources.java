package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.world.item.ItemStack;

/** Bounded adapter for the pinned Fabric API: custom ArmorRenderer output is not separable. */
public final class FabricEquipmentSources implements EquipmentSources {
    private static boolean reportedIncompatibleRegistry;

    @Override
    public boolean usesStandardRenderer(ItemStack source) {
        try {
            // Fabric exposes registration but no public lookup. Keep this single internal
            // dependency here; an incompatible future API degrades to neutral, never a false split.
            return ArmorRendererRegistryImpl.get(source.getItem()) == null;
        } catch (LinkageError incompatible) {
            if (!reportedIncompatibleRegistry) {
                reportedIncompatibleRegistry = true;
                LeggingsContent.LOG.warn("Cannot inspect Fabric armor renderers; using neutral mixed leggings", incompatible);
            }
            return false;
        }
    }
}
