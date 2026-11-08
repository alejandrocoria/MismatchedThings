package games.alejandrocoria.mismatchedleggings.test.undergarden.mixin;

import games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quek.undergarden.inventory.InfuserMenu;

/** Native quick-move consults a precomputed recipe-property set, not recipe.matches. */
@Mixin(InfuserMenu.class)
abstract class InfuserMenuCandidateMixin {
    @Inject(method = "canInfuse", at = @At("RETURN"), cancellable = true)
    private void spike$admit(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && InfusionSpike.admitMenu(stack)) {
            callback.setReturnValue(true);
        }
    }
}
