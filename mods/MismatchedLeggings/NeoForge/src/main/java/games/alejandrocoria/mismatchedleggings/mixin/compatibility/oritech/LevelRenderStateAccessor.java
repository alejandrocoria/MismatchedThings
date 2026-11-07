package games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRenderStateAccessor {
    @Accessor("levelRenderState")
    LevelRenderState mismatchedleggings$levelState();
}
