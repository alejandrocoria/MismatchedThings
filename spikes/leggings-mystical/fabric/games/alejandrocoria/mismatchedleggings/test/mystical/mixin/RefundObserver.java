package games.alejandrocoria.mismatchedleggings.test.mystical.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import games.alejandrocoria.mismatchedleggings.test.mystical.MysticalCandidate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class RefundObserver {
    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void spike$capture(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
                               @Share("spikeRefund") LocalRef<MysticalCandidate.Capture> capture) {
        capture.set(MysticalCandidate.capture((ItemStack) (Object) this, damage, owner));
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void spike$complete(int damage, ServerPlayer owner, Consumer<Item> callback, CallbackInfo ci,
                                @Share("spikeRefund") LocalRef<MysticalCandidate.Capture> capture) {
        MysticalCandidate.complete(capture.get());
    }
}
