package games.alejandrocoria.mismatchedboots.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int mismatchedboots$getActiveLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] mismatchedboots$getLayers();

    @Accessor("cachedModelBoundingBox")
    void mismatchedboots$setCachedModelBoundingBox(@Nullable AABB boundingBox);
}
