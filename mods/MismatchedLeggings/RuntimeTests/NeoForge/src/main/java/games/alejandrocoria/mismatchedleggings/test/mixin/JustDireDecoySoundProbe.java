package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.JustDireDecoyChecks;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe actual native emission calls for both the oracle and production adapter. */
@Mixin(targets = "com.direwolf20.justdirethings.common.items.interfaces.Helpers", remap = false)
public abstract class JustDireDecoySoundProbe {
    @Inject(method = "playSoundToAll(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/sounds/SoundEvent;FF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void sound(Player player, SoundEvent sound, float volume, float pitch, CallbackInfo callback) {
        if (JustDireDecoyChecks.captureSounds) {
            JustDireDecoyChecks.sounds.add(sound.location());
            callback.cancel();
        }
    }
}
