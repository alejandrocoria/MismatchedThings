package games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingLayersAccess {
    @Accessor("layers")
    List<RenderLayer<?, ?>> rootsSpike$layers();
}
