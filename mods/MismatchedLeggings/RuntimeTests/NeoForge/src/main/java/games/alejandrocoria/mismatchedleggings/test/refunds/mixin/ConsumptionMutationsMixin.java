package games.alejandrocoria.mismatchedleggings.test.refunds.mixin;

import games.alejandrocoria.mismatchedleggings.test.refunds.RefundMutations;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class ConsumptionMutationsMixin {
    @Inject(method = "shrink", at = @At("HEAD"), cancellable = true)
    private void refundTest$before(int count, CallbackInfo info) {
        if (RefundMutations.before((ItemStack) (Object) this)) {
            info.cancel();
        }
    }

    @Inject(method = "shrink", at = @At("RETURN"))
    private void refundTest$after(int count, CallbackInfo info) {
        RefundMutations.after((ItemStack) (Object) this);
    }
}
