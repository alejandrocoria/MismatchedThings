package games.alejandrocoria.mismatchedleggings.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("localTransform")
    Matrix4f mismatchedleggings$getLocalTransform();

    @Accessor("foilType")
    ItemStackRenderState.FoilType mismatchedleggings$getFoilType();

    @Accessor("quads")
    List<BakedQuad> mismatchedleggings$getQuads();

    @Accessor("specialRenderer")
    @Nullable SpecialModelRenderer<Object> mismatchedleggings$getSpecialRenderer();
}
