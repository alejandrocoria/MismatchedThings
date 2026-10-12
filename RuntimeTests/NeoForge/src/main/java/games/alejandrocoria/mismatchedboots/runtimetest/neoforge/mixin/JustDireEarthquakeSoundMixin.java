package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeServerGuardPlugin;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
abstract class JustDireEarthquakeSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"))
    private void sound(Entity excluded, double x, double y, double z, SoundEvent event, SoundSource source, float volume, float pitch, CallbackInfo ci) {
        if (event == SoundEvents.MACE_SMASH_GROUND_HEAVY) {
            if (source != SoundSource.PLAYERS || volume != 1 || pitch != .5f) { throw new AssertionError("Wrong Earthquake sound"); }
            JustDireEarthquakeServerGuardPlugin.heavySounds++;
        }
    }
}
