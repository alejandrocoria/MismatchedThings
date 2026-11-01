package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CrafterBlock.class)
public interface CrafterInvoker {
    @Invoker("dispenseFrom")
    void leggings$dispense(BlockState state, ServerLevel level, BlockPos pos);
}
