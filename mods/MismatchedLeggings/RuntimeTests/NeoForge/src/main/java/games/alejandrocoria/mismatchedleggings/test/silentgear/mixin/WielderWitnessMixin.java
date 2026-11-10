package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearerApi;
import games.alejandrocoria.mismatchedleggings.test.silentgear.WielderChecks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Witness the product boundary without changing the native methods whose final graph is proved. */
@Mixin(value = SilentGearWearerApi.class, remap = false)
abstract class WielderWitnessMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void before(Object effect, Object context, Player player, boolean selected, CallbackInfo ci) {
        WielderChecks.before(context, player, selected);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void after(Object effect, Object context, Player player, boolean selected, CallbackInfo ci) {
        WielderChecks.after(context, player);
    }
}
