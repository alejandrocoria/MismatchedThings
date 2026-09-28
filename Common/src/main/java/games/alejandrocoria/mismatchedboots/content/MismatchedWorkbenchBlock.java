package games.alejandrocoria.mismatchedboots.content;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MismatchedWorkbenchBlock extends Block {
    public static final MapCodec<MismatchedWorkbenchBlock> CODEC = simpleCodec(MismatchedWorkbenchBlock::new);
    private static final Component TITLE = Component.translatable("container.mismatchedboots.mismatched_workbench");

    public MismatchedWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MismatchedWorkbenchBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new MismatchedWorkbenchMenu(
                        containerId, inventory, ContainerLevelAccess.create(level, pos)),
                TITLE);
    }
}
