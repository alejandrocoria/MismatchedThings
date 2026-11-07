package games.alejandrocoria.mismatchedleggings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechRenderState;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Native model hooks stay standard; Exo's separate Gecko path uses its bounded capture. */
public final class NeoForgeEquipmentSources implements EquipmentSources {
    private static final Identifier EXO = Identifier.parse("oritech:exo_leggings");

    @Override
    public boolean usesStandardRenderer(ItemStack source) {
        return !EXO.equals(BuiltInRegistries.ITEM.getKey(source.getItem()));
    }

    @Override
    public EquipmentCapture captureCustom(ItemStack source, Model<?> model, Object state, boolean foil,
                                           PoseStack pose, int light, int outline) {
        // The optional attachment exists only when the native API passed the client mixin guard.
        // Keep the Gecko-linked implementation out of ordinary/absent-mod requests.
        if (!EXO.equals(BuiltInRegistries.ITEM.getKey(source.getItem())) || !(model instanceof HumanoidModel<?>)
                || !(state instanceof HumanoidRenderState humanoid) || !(state instanceof OritechRenderState)
                || humanoid.legsEquipment.hasFoil() != foil) {
            return null;
        }
        return OritechEquipmentCapture.capture(source, humanoid, pose, outline);
    }
}
