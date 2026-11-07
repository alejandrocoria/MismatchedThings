package games.alejandrocoria.mismatchedleggings.spike.aether;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;

/** Executes native references independently from the production clipping implementation. */
final class AetherVertices implements VertexConsumer {
    private final List<Vertex> vertices = new ArrayList<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Vertex> model(RecordingSubmits.Node<?> node) {
        var output = new AetherVertices();
        var pose = new PoseStack();
        pose.last().set(node.pose());
        ((Model) node.model()).setupAnim(node.state());
        node.model().renderToBuffer(pose, output, node.light(), node.overlay(), node.color());
        return List.copyOf(output.vertices);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Vertex> part(RecordingSubmits.Node<?> node, ModelPart part) {
        var output = new AetherVertices();
        var pose = new PoseStack();
        pose.last().set(node.pose());
        ((Model) node.model()).setupAnim(node.state());
        node.model().root().translateAndRotate(pose);
        part.render(pose, output, node.light(), node.overlay(), node.color());
        return List.copyOf(output.vertices);
    }

    record Vertex(float x, float y, float z, float u, float v, int color) {}

    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        vertices.add(new Vertex(x, y, z, u, v, color));
    }
    public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
    public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
    public VertexConsumer setColor(int color) { return this; }
    public VertexConsumer setUv(float u, float v) { return this; }
    public VertexConsumer setUv1(int u, int v) { return this; }
    public VertexConsumer setUv2(int u, int v) { return this; }
    public VertexConsumer setNormal(float x, float y, float z) { return this; }
    public VertexConsumer setLineWidth(float width) { return this; }
}
