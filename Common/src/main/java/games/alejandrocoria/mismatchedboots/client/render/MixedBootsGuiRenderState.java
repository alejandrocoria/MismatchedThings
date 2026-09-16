package games.alejandrocoria.mismatchedboots.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

/** A freshly prepared, read-only GUI composition; never passed back to the model resolver. */
public final class MixedBootsGuiRenderState extends TrackingItemStackRenderState {
    private final List<TrackingItemStackRenderState> sources;

    MixedBootsGuiRenderState(
            TrackingItemStackRenderState left, TrackingItemStackRenderState right, boolean clipped) {
        sources = List.of(left, right);
        appendModelIdentityElement(new CompositionIdentity(clipped, left.getModelIdentity(), right.getModelIdentity()));
        if (left.isAnimated() || right.isAnimated()) {
            setAnimated();
        }
        setOversizedInGui(left.isOversizedInGui() || right.isOversizedInGui());
    }

    public List<TrackingItemStackRenderState> sources() {
        return sources;
    }

    @Override
    public boolean isEmpty() {
        return sources.stream().allMatch(TrackingItemStackRenderState::isEmpty);
    }

    @Override
    public boolean usesBlockLight() {
        return sources.stream().filter(source -> !source.isEmpty()).findFirst()
                .map(TrackingItemStackRenderState::usesBlockLight).orElse(false);
    }

    @Override
    public void visitExtents(Consumer<Vector3fc> output) {
        sources.forEach(source -> source.visitExtents(output));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline) {
        sources.forEach(source -> source.submit(pose, collector, light, overlay, outline));
    }

    private record CompositionIdentity(boolean clipped, Object left, Object right) {
    }
}
