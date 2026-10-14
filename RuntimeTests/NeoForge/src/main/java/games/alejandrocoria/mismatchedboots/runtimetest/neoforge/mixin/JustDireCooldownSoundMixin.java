package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireCooldownFeedbackTestSupport;
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
        if (!((Level) (Object) this).isClientSide() && JustDireCooldownFeedbackTestSupport.capturingSounds
                && (event == SoundEvents.CONDUIT_DEACTIVATE || event == SoundEvents.ENDER_EYE_DEATH
                || event == SoundEvents.MACE_SMASH_GROUND || event == SoundEvents.MACE_SMASH_GROUND_HEAVY)) {
            var player = JustDireCooldownFeedbackTestSupport.observedPlayer;
            // Reconnect creates a new server/player; observe that level's actual root, not an old registry snapshot.
            if (player != null && ((Level) (Object) this).getPlayerByUUID(player.getUUID()) instanceof net.minecraft.server.level.ServerPlayer current) {
                player = current;
            }
            var clocks = player == null ? java.util.List.<JustDireCooldownFeedbackTestSupport.Clock>of() : JustDireCooldownFeedbackTestSupport.before(player);
            JustDireCooldownFeedbackTestSupport.sounds.add(new JustDireCooldownFeedbackTestSupport.Sound(event, source, x, y, z, volume, pitch, excluded != null, clocks));
            if (JustDireCooldownFeedbackTestSupport.failNextSound) {
                JustDireCooldownFeedbackTestSupport.failNextSound = false;
                throw new JustDireCooldownFeedbackTestSupport.SoundProbeFailure();
            }
        }
    }
}
