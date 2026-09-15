package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.GuiItemResolutionProbe;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverProbeMixin {
    @Inject(method = "updateForTopItem", at = @At("HEAD"))
    private void mismatchedboots$recordTopItemResolution(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo callback) {
        GuiItemResolutionProbe.record(stack, displayContext, level, owner, seed);
    }
}
