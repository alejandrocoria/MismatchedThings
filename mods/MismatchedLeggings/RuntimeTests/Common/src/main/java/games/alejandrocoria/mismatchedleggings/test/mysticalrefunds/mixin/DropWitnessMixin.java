package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.mixin;

import games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class DropWitnessMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void leggings$acceptedDrop(Entity entity, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && entity instanceof ItemEntity item) { RefundControl.drops.add(item); }
    }
}
