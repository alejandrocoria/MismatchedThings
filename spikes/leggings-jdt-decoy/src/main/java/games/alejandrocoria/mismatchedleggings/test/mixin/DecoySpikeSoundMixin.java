package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.DecoySpike;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.direwolf20.justdirethings.common.items.interfaces.Helpers", remap = false)
abstract class DecoySpikeSoundMixin {
    @Inject(method = "playSoundToAll(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/sounds/SoundEvent;FF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void capture(Player player, SoundEvent event, float volume, float pitch, CallbackInfo ci) {
        if (DecoySpike.captureSounds) {
            DecoySpike.sound(event);
            ci.cancel();
        }
    }
}
