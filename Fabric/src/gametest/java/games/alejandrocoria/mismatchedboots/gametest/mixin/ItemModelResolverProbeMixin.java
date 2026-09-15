package games.alejandrocoria.mismatchedboots.gametest.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.gametest.GuiItemResolutionProbe;
import games.alejandrocoria.mismatchedboots.gametest.ItemLayerResolutionProbe;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricLayerRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
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

    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void mismatchedboots$beginLayerResolution(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo callback,
            @Share("mismatchedboots$firstLayer") LocalIntRef firstLayer) {
        firstLayer.set(((ItemStackRenderStateAccessor) (Object) state)
                .mismatchedboots$getActiveLayerCount());
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        if (modelId != null) {
            ItemLayerResolutionProbe.record(modelId);
        }
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void mismatchedboots$finishLayerResolution(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo callback,
            @Share("mismatchedboots$firstLayer") LocalIntRef firstLayer) {
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        if (modelId == null || !ItemLayerResolutionProbe.requiresExtensionGeometry(modelId)) {
            return;
        }

        ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) (Object) state;
        for (int index = firstLayer.get(); index < accessor.mismatchedboots$getActiveLayerCount(); index++) {
            ((FabricLayerRenderState) (Object) accessor.mismatchedboots$getLayers()[index])
                    .emitter()
                    .square(Direction.SOUTH, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F)
                    .emit();
        }
    }
}
