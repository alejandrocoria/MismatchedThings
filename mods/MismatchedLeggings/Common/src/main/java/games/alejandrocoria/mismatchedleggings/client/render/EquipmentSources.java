package games.alejandrocoria.mismatchedleggings.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

/** Loader boundary for renderers that bypass the native equipment submission path. */
public interface EquipmentSources {
    EquipmentSources INSTANCE = ServiceLoader.load(EquipmentSources.class).findFirst().orElseThrow();

    boolean usesStandardRenderer(ItemStack source);

    /** Known custom sources only; unsupported output must not fall through to the standard path. */
    default BufferedEquipment captureCustom(ItemStack source, Model<?> model, Object state, boolean foil,
                                             PoseStack pose, int light, int outline) {
        return null;
    }
}
