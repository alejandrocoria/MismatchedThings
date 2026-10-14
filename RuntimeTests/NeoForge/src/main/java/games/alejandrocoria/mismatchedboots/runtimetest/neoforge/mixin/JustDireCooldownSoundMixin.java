package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireCooldownFeedbackSpike;
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
abstract class JustDireCooldownSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"))
    private void fixtureSound(Entity excluded, double x, double y, double z, SoundEvent event, SoundSource source, float volume, float pitch, CallbackInfo ci) {
        if (!((Level) (Object) this).isClientSide() && JustDireCooldownFeedbackSpike.capturingSounds
                && (event == SoundEvents.CONDUIT_DEACTIVATE || event == SoundEvents.ENDER_EYE_DEATH
                || event == SoundEvents.MACE_SMASH_GROUND || event == SoundEvents.MACE_SMASH_GROUND_HEAVY)) {
            var player = JustDireCooldownFeedbackSpike.observedPlayer;
            var clocks = player == null ? java.util.List.<JustDireCooldownFeedbackSpike.Clock>of() : JustDireCooldownFeedbackSpike.before(player);
            JustDireCooldownFeedbackSpike.sounds.add(new JustDireCooldownFeedbackSpike.Sound(event, source, x, y, z, volume, pitch, excluded != null, clocks));
            if (JustDireCooldownFeedbackSpike.failNextSound) {
                JustDireCooldownFeedbackSpike.failNextSound = false;
                throw new JustDireCooldownFeedbackSpike.SoundProbeFailure();
            }
        }
    }
}
