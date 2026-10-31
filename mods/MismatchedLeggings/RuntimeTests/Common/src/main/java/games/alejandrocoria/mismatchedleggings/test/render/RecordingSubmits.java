package games.alejandrocoria.mismatchedleggings.test.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;

public final class RecordingSubmits extends SubmitNodeStorage {
    public final List<Node<?>> nodes = new ArrayList<>();

    @Override
    public SubmitNodeCollection order(int order) {
        return new SubmitNodeCollection(this) {
            @Override
            public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                                        int light, int overlay, int color, TextureAtlasSprite sprite,
                                        int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
                nodes.add(new Node<>(order, model, state, pose.last().copy(), type, light, overlay,
                        color, sprite, outline, crumbling));
            }
        };
    }

    public record Node<S>(int order, Model<? super S> model, S state, PoseStack.Pose pose, RenderType type,
                          int light, int overlay, int color, TextureAtlasSprite sprite, int outline,
                          ModelFeatureRenderer.CrumblingOverlay crumbling) {}
}
