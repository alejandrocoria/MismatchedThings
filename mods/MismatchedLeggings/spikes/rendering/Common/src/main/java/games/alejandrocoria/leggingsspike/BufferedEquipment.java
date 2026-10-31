package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Buffers the native equipment path's model submissions, not arbitrary custom renderers. */
public final class BufferedEquipment extends SubmitNodeStorage {
    private final boolean left;
    private final Map<Model<?>, Model<?>> views = new IdentityHashMap<>();
    private final List<Node<?>> nodes = new ArrayList<>();
    public String rejectionReason;

    public BufferedEquipment(boolean left) {
        this.left = left;
    }

    @Override
    public SubmitNodeCollection order(int order) {
        return new SubmitNodeCollection(this) {
            @Override
            public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType type,
                                        int light, int overlay, int color, TextureAtlasSprite sprite,
                                        int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
                nodes.add(new Node<>(order, model, state, poseStack.last().copy(), type,
                        light, overlay, color, sprite, outline, crumbling));
            }
        };
    }

    public boolean preflight() {
        if (nodes.isEmpty()) {
            rejectionReason = "No native model submissions";
            return false;
        }
        try {
            for (Node<?> node : nodes) {
                views.computeIfAbsent(node.model(), model -> new LeggingsView<>(model, left));
            }
            return true;
        } catch (IllegalArgumentException unsupported) {
            rejectionReason = unsupported.getMessage();
            return false;
        }
    }

    public void flush(SubmitNodeCollector destination) {
        for (Node<?> node : nodes) {
            flush(node, destination);
        }
    }

    @SuppressWarnings("unchecked")
    private <S> void flush(Node<S> node, SubmitNodeCollector destination) {
        Model<? super S> model = (Model<? super S>) views.get(node.model());
        if (model == null) {
            throw new IllegalStateException("Flush requires successful preflight");
        }
        PoseStack pose = new PoseStack();
        pose.last().set(node.pose());
        destination.order(node.order()).submitModel(model, node.state(), pose, node.type(), node.light(),
                node.overlay(), node.color(), node.sprite(), node.outline(), node.crumbling());
    }

    public int size() {
        return nodes.size();
    }

    List<Node<?>> snapshot() {
        return List.copyOf(nodes);
    }

    record Node<S>(int order, Model<? super S> model, S state, PoseStack.Pose pose, RenderType type,
                   int light, int overlay, int color, TextureAtlasSprite sprite, int outline,
                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
    }
}
