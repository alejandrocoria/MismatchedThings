package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairTestSupport;
import net.minecraft.core.BlockPos;
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
public abstract class JustDireLavaRepairSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"))
    private void observe(Entity except, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch, CallbackInfo result) {
        if (JustDireLavaRepairTestSupport.capture && sound == SoundEvents.LAVA_EXTINGUISH) {
            if (category != SoundSource.BLOCKS || volume != 1 || pitch != 1) { throw new AssertionError("Wrong completion sound parameters"); }
            JustDireLavaRepairTestSupport.sounds.add(pos.immutable());
        }
    }
}
