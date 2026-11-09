package games.alejandrocoria.mismatchedleggings.mixin.compatibility.mysticalagriculture;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalRefundCompatibility;
import net.minecraft.server.level.ServerPlayer;
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
    @Unique private static boolean mismatchedleggings$maRefundReady() { return false; }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private void mismatchedleggings$maCapture(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("leggingsMaRefund") LocalRef<MysticalRefundCompatibility.Capture> capture) {
        capture.set(MysticalRefundCompatibility.capture(mismatchedleggings$maRefundReady(), (ItemStack) (Object) this, damage, owner));
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void mismatchedleggings$maArm(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("leggingsMaRefund") LocalRef<MysticalRefundCompatibility.Capture> capture) {
        MysticalRefundCompatibility.arm(capture.get());
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V", shift = At.Shift.AFTER), require = 0)
    private void mismatchedleggings$maConsumed(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("leggingsMaRefund") LocalRef<MysticalRefundCompatibility.Capture> capture) {
        MysticalRefundCompatibility.consumed(capture.get());
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private void mismatchedleggings$maComplete(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
            @Share("leggingsMaRefund") LocalRef<MysticalRefundCompatibility.Capture> capture) {
        MysticalRefundCompatibility.complete(capture.get());
    }
}
