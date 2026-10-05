package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ModelFeatureRenderer.Storage.class)
public interface ModelSubmitStorageAccessor {
    @Accessor("solidModelSubmits")
    Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> mismatchedboots$getSolidModelSubmits();

    @Accessor("translucentModelSubmits")
    List<SubmitNodeStorage.TranslucentModelSubmit<?>> mismatchedboots$getTranslucentModelSubmits();
}
