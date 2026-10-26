package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = LoadingErrorScreen.class, remap = false)
abstract class LoadingErrorScreenMixin {
    @Shadow
    @Final
    private List<?> modLoadErrors;

    @Shadow
    @Final
    private Runnable nextScreenTask;

    @Inject(method = "init", at = @At("TAIL"))
    private void mismatchedboots$continuePastWarnings(CallbackInfo callbackInfo) {
        if (Boolean.getBoolean("mismatchedboots.runtimetest.dismissWarnings") && modLoadErrors.isEmpty()) {
            nextScreenTask.run();
        }
    }
}
