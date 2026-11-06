package games.alejandrocoria.mismatchedleggings.mixin.compatibility.aquaculture;

import games.alejandrocoria.mismatchedleggings.compatibility.aquaculture.AquacultureBuoyancy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void mismatchedleggings$buoyancy(CallbackInfoReturnable<Double> result) {
        double original = result.getReturnValueD();
        double gravity = AquacultureBuoyancy.gravity((Entity) (Object) this, original);
        if (gravity != original) {
            result.setReturnValue(gravity);
        }
    }
}
