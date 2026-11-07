package games.alejandrocoria.mismatchedleggings.test.oritech.mixin;

import games.alejandrocoria.mismatchedleggings.test.oritech.NativeExoBridge;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class SpikeWearerProbe {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void wearer(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo callback) {
        if (state instanceof HumanoidRenderState humanoid) {
            NativeExoBridge.remember(humanoid, entity);
        }
    }
}
