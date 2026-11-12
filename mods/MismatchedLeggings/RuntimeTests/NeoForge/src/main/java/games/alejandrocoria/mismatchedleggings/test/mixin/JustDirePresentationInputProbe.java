package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.JustDirePresentationClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only synchronous tooltip captures override Shift; gameplay input remains real event delivery. */
@Mixin(Minecraft.class)
abstract class JustDirePresentationInputProbe {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private void leggings$jdtShift(CallbackInfoReturnable<Boolean> callback) {
        var shift = JustDirePresentationClient.shift;
        if (shift != null) { callback.setReturnValue(shift); }
    }
}
