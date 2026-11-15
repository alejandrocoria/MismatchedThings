package games.alejandrocoria.mismatchedthings.mixin.compatibility.mysticalagriculture;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture.MysticalRefundObserver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = ItemStack.class, priority = 1100)
abstract class MysticalRefundMixin {
    @Unique
    private static boolean mismatchedthings$maRefundReady() {
        return false;
    }

    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private void mismatchedthings$maCapture(int damage, LivingEntity owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("thingsMaRefund") LocalRef<MysticalRefundObserver.Capture> capture) {
        capture.set(MysticalRefundObserver.capture(mismatchedthings$maRefundReady(), (ItemStack) (Object) this, damage, owner));
    }

    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void mismatchedthings$maArm(int damage, LivingEntity owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("thingsMaRefund") LocalRef<MysticalRefundObserver.Capture> capture) {
        MysticalRefundObserver.arm(capture.get());
    }

    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V", shift = At.Shift.AFTER), require = 0)
    private void mismatchedthings$maConsumed(int damage, LivingEntity owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("thingsMaRefund") LocalRef<MysticalRefundObserver.Capture> capture) {
        MysticalRefundObserver.consumed(capture.get());
    }

    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private void mismatchedthings$maComplete(int damage, LivingEntity owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("thingsMaRefund") LocalRef<MysticalRefundObserver.Capture> capture) {
        MysticalRefundObserver.complete(capture.get());
    }
}
