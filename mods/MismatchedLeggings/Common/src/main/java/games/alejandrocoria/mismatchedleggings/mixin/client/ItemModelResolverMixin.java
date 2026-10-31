package games.alejandrocoria.mismatchedleggings.mixin.client;

import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$appendMixedLayers(
            ItemStackRenderState output,
            ItemStack stack,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed,
            CallbackInfo callback) {
        if (MixedLeggingsItemRenderer.appendLayers(
                output,
                stack,
                (ItemModelResolver) (Object) this,
                displayContext,
                level,
                owner,
                seed)) {
            callback.cancel();
        }
    }
}
