package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireGroundStompServerGuardPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JustDireGroundStompCompatibility.class)
abstract class JustDireGroundStompPostCommitMixin {
    @Inject(method = "activate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private static void fail(CallbackInfoReturnable<Boolean> callback) {
        if (JustDireGroundStompServerGuardPlugin.fault == 4) {
            throw new JustDireGroundStompServerGuardPlugin.PostCommitFailure();
        }
    }
}
