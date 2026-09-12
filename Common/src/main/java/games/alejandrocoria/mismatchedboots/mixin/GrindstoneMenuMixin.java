package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.interaction.MixedBootsGrindstonePolicy;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$rejectMixedBoots(
            ItemStack input,
            ItemStack additional,
            CallbackInfoReturnable<ItemStack> callback) {
        if (MixedBootsGrindstonePolicy.rejects(input, additional)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
