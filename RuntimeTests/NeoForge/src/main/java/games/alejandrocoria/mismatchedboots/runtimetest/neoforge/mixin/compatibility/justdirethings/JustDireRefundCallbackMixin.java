package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRefundFaults;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class JustDireRefundCallbackMixin {
    @Inject(method = "onEquippedItemBroken", at = @At("HEAD"))
    private void refundTest$callback(Item item, EquipmentSlot slot, CallbackInfo info) {
        JustDireRefundFaults.broken((LivingEntity) (Object) this, slot);
    }
}
