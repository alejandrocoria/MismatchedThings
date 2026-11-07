package games.alejandrocoria.mismatchedleggings.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

/** Loader boundary for renderers that bypass the native equipment submission path. */
public interface EquipmentSources {
    EquipmentSources INSTANCE = ServiceLoader.load(EquipmentSources.class).findFirst().orElseThrow();

    boolean usesStandardRenderer(ItemStack source);

    /** Native layer by default; null rejects a source whose alternate route is unsafe. */
    default EquipmentClientInfo.LayerType selectLayer(ItemStack source, EquipmentClientInfo.LayerType requested,
                                                     EquipmentLayerRenderer renderer) {
        return requested;
    }

    /** Known custom sources only; unsupported output must not fall through to the standard path. */
    default EquipmentCapture captureCustom(ItemStack source, Model<?> model, Object state, boolean foil,
                                             PoseStack pose, int light, int outline) {
        return null;
    }
}
