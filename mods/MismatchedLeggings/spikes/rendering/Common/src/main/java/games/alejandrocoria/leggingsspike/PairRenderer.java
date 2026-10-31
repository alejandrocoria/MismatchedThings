package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.function.Predicate;

/** Research route: no production fallback or support claim for arbitrary custom collectors. */
public final class PairRenderer {
    public static int successfulPairs;
    public static int rejectedPairs;
    public static String rejectionReason;
    public static Predicate<ItemStack> supportsSource = stack -> true;

    private PairRenderer() {
    }

    public static <S> boolean render(EquipmentLayerRenderer renderer, Model<? super S> nativeModel, S state,
                                     EquipmentClientInfo.LayerType layer, ItemStack left, ItemStack right,
                                     PoseStack pose, SubmitNodeCollector destination, int light, int outline) {
        BufferedEquipment[] pending = {new BufferedEquipment(true), new BufferedEquipment(false)};
        ItemStack[] sources = {left, right};
        if (!supportsSource.test(left) || !supportsSource.test(right)) {
            rejectionReason = "Source uses a separate custom renderer";
            rejectedPairs++;
            return false;
        }
        for (int i = 0; i < sources.length; i++) {
            Equippable equippable = sources[i].get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.slot() != EquipmentSlot.LEGS || equippable.assetId().isEmpty()) {
                rejectionReason = "Missing leggings equipment asset";
                rejectedPairs++;
                return false;
            }
            // Native model resolution, material hooks and pass ordering run exactly once.
            renderer.renderLayers(layer, equippable.assetId().orElseThrow(), nativeModel, state, sources[i],
                    pose, pending[i], light, outline);
        }
        boolean leftSupported = pending[0].preflight();
        boolean rightSupported = pending[1].preflight();
        if (!leftSupported || !rightSupported) {
            rejectionReason = leftSupported ? pending[1].rejectionReason : pending[0].rejectionReason;
            rejectedPairs++;
            return false;
        }
        pending[0].flush(destination);
        pending[1].flush(destination);
        successfulPairs++;
        return true;
    }
}
