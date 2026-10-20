package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnetApi;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearMagnetTest;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SilentGearMagnetApi.class, remap = false)
abstract class SilentGearMagnetWitnessMixin {
    @Inject(method = "invoke", at = @At("HEAD"))
    private void count(Object effect, Object context, Player player, CallbackInfo ci) throws Exception {
        SilentGearMagnetTest.beforeCall(context);
    }

    @Inject(method = "invoke", at = @At("RETURN"))
    private void completed(CallbackInfo ci) { SilentGearMagnetTest.afterCall(); }
}
