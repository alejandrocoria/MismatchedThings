package games.alejandrocoria.mismatchedleggings.mixin.client;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void mismatchedleggings$clearGeometry(CallbackInfo callback) {
        LeggingsGeometryCache.clear();
    }
}
