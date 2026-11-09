package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.mixin;

import games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class DeliveryMixin {
    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void leggings$delivery(ItemStack item, CallbackInfo ci) {
        if (RefundControl.delivery((Inventory) (Object) this)) { ci.cancel(); }
    }
}
