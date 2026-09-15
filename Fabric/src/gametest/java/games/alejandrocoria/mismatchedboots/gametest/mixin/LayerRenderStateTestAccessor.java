package games.alejandrocoria.mismatchedboots.gametest.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateTestAccessor {
    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> mismatchedboots$getSpecialRenderer();

    @Accessor("tintLayers")
    IntList mismatchedboots$getTintLayers();
}
