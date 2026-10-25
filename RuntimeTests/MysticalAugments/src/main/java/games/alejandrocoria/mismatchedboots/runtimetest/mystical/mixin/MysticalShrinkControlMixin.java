package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalRefundControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class MysticalShrinkControlMixin {
    @Inject(method = "shrink", at = @At("HEAD"), cancellable = true)
    private void prevent(int count, CallbackInfo callback) {
        if ((Object) this == MysticalRefundControl.root && MysticalRefundControl.prevent) {
            callback.cancel();
        }
    }
}
