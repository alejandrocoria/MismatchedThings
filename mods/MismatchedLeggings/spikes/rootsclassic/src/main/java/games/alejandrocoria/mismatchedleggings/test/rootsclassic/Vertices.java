package games.alejandrocoria.mismatchedleggings.test.rootsclassic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Executes the queued native model; independent selected-part output is the split oracle. */
final class Vertices implements VertexConsumer {
    private final List<Vertex> vertices = new ArrayList<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Vertex> model(RecordingSubmits.Node<?> node) {
        var result = new Vertices();
        var pose = new PoseStack();
        pose.last().set(node.pose());
        ((Model) node.model()).setupAnim(node.state());
        node.model().renderToBuffer(pose, result, node.light(), node.overlay(), node.color());
        return List.copyOf(result.vertices);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Vertex> part(RecordingSubmits.Node<?> node, ModelPart part) {
        var result = new Vertices();
        var pose = new PoseStack();
        pose.last().set(node.pose());
        ((Model) node.model()).setupAnim(node.state());
        node.model().root().translateAndRotate(pose);
        part.render(pose, result, node.light(), node.overlay(), node.color());
        return List.copyOf(result.vertices);
    }

    static List<Vertex> sorted(List<Vertex> vertices) {
        return vertices.stream().sorted(Comparator.comparing(Vertex::toString)).toList();
    }

    record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int overlay, int color) {
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz, light, overlay, color));
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int color) { return this; }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}
