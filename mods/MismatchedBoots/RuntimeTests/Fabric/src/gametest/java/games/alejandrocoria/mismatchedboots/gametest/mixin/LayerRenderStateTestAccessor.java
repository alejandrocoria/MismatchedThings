package games.alejandrocoria.mismatchedboots.gametest.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.sprite.Material;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateTestAccessor {
    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> mismatchedboots$getSpecialRenderer();

    @Accessor("tintLayers")
    IntList mismatchedboots$getTintLayers();

    @Accessor("usesBlockLight")
    boolean mismatchedboots$usesBlockLight();

    @Accessor("particleMaterial")
    Material.Baked mismatchedboots$getParticleMaterial();

    @Accessor("itemTransform")
    ItemTransform mismatchedboots$getItemTransform();

    @Accessor("extents")
    Supplier<Vector3fc[]> mismatchedboots$getExtents();
}
