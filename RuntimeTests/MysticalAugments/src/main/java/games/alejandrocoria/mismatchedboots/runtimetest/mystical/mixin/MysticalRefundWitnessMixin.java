package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalRefundControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class MysticalRefundWitnessMixin {
    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void delivery(ItemStack stack, CallbackInfo callback) {
        if (MysticalRefundControl.delivery((Inventory) (Object) this)) { callback.cancel(); }
    }
}
