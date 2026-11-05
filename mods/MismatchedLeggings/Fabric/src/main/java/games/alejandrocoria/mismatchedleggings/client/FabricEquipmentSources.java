package games.alejandrocoria.mismatchedleggings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.compatibility.betterend.CrystaliteEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.client.model.Model;
import net.minecraft.world.item.ItemStack;

/** Standard equipment routing plus bounded adapters for known custom Fabric renderers. */
public final class FabricEquipmentSources implements EquipmentSources {
    private static boolean reportedIncompatibleRegistry;

    @Override
    public BufferedEquipment captureCustom(ItemStack source, Model<?> model, Object state, boolean foil,
                                            PoseStack pose, int light, int outline) {
        return CrystaliteEquipmentCapture.capture(source, model, state, foil, pose, light, outline);
    }

    @Override
    public boolean usesStandardRenderer(ItemStack source) {
        try {
            // Fabric exposes registration but no public lookup. If it becomes unavailable,
            // keep unsupported custom renderers off the standard path, never a false split.
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
