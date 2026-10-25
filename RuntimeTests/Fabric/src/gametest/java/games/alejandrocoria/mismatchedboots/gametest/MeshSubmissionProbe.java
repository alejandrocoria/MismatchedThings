package games.alejandrocoria.mismatchedboots.gametest;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MeshSubmissionProbe {
    private static final List<Submission> submissions = new ArrayList<>();
    private static boolean armed;

    private MeshSubmissionProbe() {
    }

    public static void arm() {
        submissions.clear();
        armed = true;
    }

    public static void record(ItemDisplayContext context, Matrix4fc pose, int[] tints,
                              List<BakedQuad> quads, @Nullable MeshView mesh, ItemStackRenderState.FoilType foil) {
        if (!armed) {
            return;
        }
        if (submissions.size() >= 128) {
            throw new AssertionError("Extended item probe exceeded its bound");
        }
        submissions.add(new Submission(context, new Matrix4f(pose),
                                       tints == null ? List.of() : java.util.Arrays.stream(tints).boxed().toList(),
                                       List.copyOf(quads), fingerprint(mesh), foil));
    }

    public static List<Submission> take() {
        armed = false;
        var result = List.copyOf(submissions);
        submissions.clear();
        return result;
    }

    public static List<Face> fingerprint(@Nullable MeshView mesh) {
        if (mesh == null) {
            return List.of();
        }
        List<Face> result = new ArrayList<>();
        mesh.forEach(quad -> result.add(face(quad)));
        return List.copyOf(result);
    }

    private static Face face(QuadView quad) {
        List<Vertex> vertices = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            vertices.add(new Vertex(quad.copyPos(index, null), quad.u(index), quad.v(index),
                                    quad.color(index), quad.lightmap(index), quad.copyNormal(index, null)));
        }
        return new Face(List.copyOf(vertices), List.of(quad.atlas(), quad.itemRenderType(), quad.chunkLayer(),
                                                       quad.emissive(), quad.diffuseShade(), quad.ambientOcclusion(), quad.shadeMode(),
                                                       Optional.ofNullable(quad.foilType()), quad.animated(), quad.tintIndex(), quad.tag(),
                                                       Optional.ofNullable(quad.cullFace()), Optional.ofNullable(quad.nominalFace())),
                        new Vector3f(quad.faceNormal()), quad.lightFace());
    }

    public record Vertex(Vector3f position, float u, float v, int color, int lightmap, @Nullable Vector3f normal) {
    }

    public record Face(List<Vertex> vertices, List<Object> properties, Vector3f normal, net.minecraft.core.Direction lightFace) {
    }

    public record Submission(ItemDisplayContext context, Matrix4fc pose, List<Integer> tints,
                             List<BakedQuad> quads, List<Face> mesh, ItemStackRenderState.FoilType foil) {
    }
}
