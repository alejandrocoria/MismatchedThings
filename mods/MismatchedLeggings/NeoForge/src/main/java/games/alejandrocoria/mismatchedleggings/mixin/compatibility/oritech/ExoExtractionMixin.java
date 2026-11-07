package games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderer.class)
abstract class ExoExtractionMixin {
    @WrapMethod(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")
    private EntityRenderState mismatchedleggings$extract(Entity entity, float partialTick, Operation<EntityRenderState> original) {
        var state = original.call(entity, partialTick);
        if (state instanceof OritechRenderState attached) {
            attached.mismatchedleggings$exoFrame(null);
        }
        if (OritechEquipmentCapture.extracting() || !(state instanceof HumanoidRenderState humanoid)
                || !(entity instanceof LivingEntity wearer) || !((Object) this instanceof LivingEntityRenderer<?, ?, ?>)) {
            return state;
        }
        for (var layer : ((LivingRendererAccessor) this).mismatchedleggings$layers()) {
            if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
                var armor = (ArmorModelAccessor) layer;
                OritechEquipmentCapture.extract(wearer, humanoid, partialTick,
                        () -> (HumanoidRenderState) original.call(entity, partialTick),
                        fresh -> armor.mismatchedleggings$model(fresh, EquipmentSlot.LEGS));
                break;
            }
        }
        return state;
    }
}
