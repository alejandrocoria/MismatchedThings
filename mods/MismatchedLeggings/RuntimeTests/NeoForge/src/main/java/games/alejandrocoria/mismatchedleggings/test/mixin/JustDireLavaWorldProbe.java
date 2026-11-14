package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.JustDireLavaObservations;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

@Mixin(Level.class)
public abstract class JustDireLavaWorldProbe {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void refuse(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> result) {
        if (pos.equals(JustDireLavaObservations.refusedBlock) && state.is(Blocks.OBSIDIAN)) {
            JustDireLavaObservations.refusedWrites++;
            result.setReturnValue(false);
        }
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            at = @At("HEAD"))
    private void observe(Entity except, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch, CallbackInfo result) {
        if (!((Level) (Object) this).isClientSide() && JustDireLavaObservations.capture && sound == SoundEvents.LAVA_EXTINGUISH) {
            require(category == SoundSource.BLOCKS && volume == 1 && pitch == 1, "Wrong native completion sound");
            JustDireLavaObservations.sounds++;
        }
    }
}
