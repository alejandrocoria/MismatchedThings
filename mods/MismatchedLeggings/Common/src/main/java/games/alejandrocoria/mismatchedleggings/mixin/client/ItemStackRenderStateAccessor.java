package games.alejandrocoria.mismatchedleggings.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int mismatchedleggings$getActiveLayerCount();

    @Accessor("activeLayerCount")
    void mismatchedleggings$setActiveLayerCount(int count);

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] mismatchedleggings$getLayers();

    @Accessor("cachedModelBoundingBox")
    void mismatchedleggings$setCachedModelBoundingBox(@Nullable AABB boundingBox);
}
