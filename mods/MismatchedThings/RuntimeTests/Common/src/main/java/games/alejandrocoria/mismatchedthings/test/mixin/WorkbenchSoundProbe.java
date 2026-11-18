package games.alejandrocoria.mismatchedthings.test.mixin;

import games.alejandrocoria.mismatchedthings.test.WorkbenchChecks;
import games.alejandrocoria.mismatchedthings.test.WorkbenchSoundChecks;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the native server broadcast, not a test hook in the menu. */
@Mixin(ServerLevel.class)
public abstract class WorkbenchSoundProbe {
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), require = 1)
    private void recordSplit(Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                             SoundSource source, float volume, float pitch, long seed, CallbackInfo callback) {
        if (sound.value().location().equals(WorkbenchSoundChecks.SOUND_ID)) {
            WorkbenchChecks.require(except == null && source == SoundSource.BLOCKS,
                    "Workbench sound excluded the acting player or used the wrong category");
            WorkbenchChecks.require(x == WorkbenchChecks.TABLE.getX() + 0.5
                    && y == WorkbenchChecks.TABLE.getY() + 0.5
                    && z == WorkbenchChecks.TABLE.getZ() + 0.5, "Workbench sound was not positioned at the station");
            WorkbenchSoundChecks.broadcasts++;
        }
    }
}
