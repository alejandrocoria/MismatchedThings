package games.alejandrocoria.mismatchedleggings.test.fabricmixin;

import games.alejandrocoria.mismatchedleggings.test.CrystaliteFailureControls;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Corrupt only our probe's input; independent native controls remain untouched. */
@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.client.compatibility.betterend.CrystaliteEquipmentCapture", remap = false)
abstract class CrystaliteMaterialProbe {
    @ModifyArg(method = "probe", at = @At(value = "INVOKE", target =
            "Lnet/fabricmc/fabric/api/client/rendering/v1/ArmorRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;"
                    + "ILnet/minecraft/client/model/HumanoidModel;)V"), index = 1)
    private static SubmitNodeCollector leggingsTest$dropOneCallback(SubmitNodeCollector collector) {
        return CrystaliteFailureControls.probeCollector(collector);
    }
}
