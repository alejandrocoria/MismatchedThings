package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalRefundControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemStack.class, priority = 1200)
abstract class MysticalRefundOuterMixin {
    @WrapOperation(method = {"applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void refundOuter(ItemStack root, int count, Operation<Void> ordinary) {
        MysticalRefundControl.before(); ordinary.call(root, count); MysticalRefundControl.after();
    }
}
