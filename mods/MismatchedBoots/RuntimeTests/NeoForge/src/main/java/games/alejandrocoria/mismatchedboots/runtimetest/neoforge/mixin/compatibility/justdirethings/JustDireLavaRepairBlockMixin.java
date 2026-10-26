package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Injects a refused world write, not a replacement repair oracle. */
@Mixin(Level.class)
public abstract class JustDireLavaRepairBlockMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void refuse(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> result) {
        if (pos.equals(JustDireLavaRepairTestSupport.refusedBlock) && state.is(Blocks.OBSIDIAN)) {
            JustDireLavaRepairTestSupport.refusedWrites++;
            result.setReturnValue(false);
            return;
        }
    }
}
