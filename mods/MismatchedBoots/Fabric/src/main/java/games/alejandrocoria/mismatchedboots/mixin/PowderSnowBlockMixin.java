package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PowderSnowBlock.class)
abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"), cancellable = true)
    private static void mismatchedboots$recognizeLeatherSource(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || !(entity instanceof LivingEntity wearer)) {
            return;
        }

        if (MixedBootsSources.anySource(wearer.getItemBySlot(EquipmentSlot.FEET), source -> source.is(Items.LEATHER_BOOTS))) {
            callback.setReturnValue(true);
        }
    }
}
