package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Known transformed waist children plus a deliberately animated reference-ownership control. */
final class WaistHierarchyChecks {
    private WaistHierarchyChecks() {
    }

    static void run() {
        MeshDefinition mesh = mesh();
        HumanoidModel<HumanoidRenderState> source = new HumanoidModel<>(LayerDefinition.create(mesh, 64, 32).bakeRoot()) {
            @Override
            public void setupAnim(HumanoidRenderState state) {
                super.setupAnim(state);
                // A child crosses the center, but its source ownership must not switch.
                body.getChild("left_ornament").x = state.ageInTicks == 2 ? -4 : 4;
                ModelPart pivot = body.getChild("pivot");
                pivot.yRot += state.ageInTicks * 0.2F;
                pivot.visible = state.ageInTicks != 3;
                pivot.skipDraw = state.ageInTicks == 4;
                pivot.getChild("crossing").zScale = 1 + state.ageInTicks * 0.1F;
                body.yScale = 1.1F;
                root().zScale = 0.9F;
            }
        };
        HumanoidRenderState a = new HumanoidRenderState();
        a.ageInTicks = 1;
        HumanoidRenderState b = new HumanoidRenderState();
        b.ageInTicks = 2;
        b.isCrouching = true;
        b.isPassenger = true;
        for (HumanoidRenderState state : new HumanoidRenderState[]{a, b}) {
            SpikeChecks.assertModel(source, state);
        }

        LeggingsView<HumanoidRenderState> queuedA = new LeggingsView<>(source, true);
        var first = SpikeChecks.vertices(queuedA, a);
        new LeggingsView<>(source, false).setupAnim(b);
        LeggingsView<HumanoidRenderState> constructedAfterB = new LeggingsView<>(source, true);
        SpikeChecks.require(first.equals(SpikeChecks.vertices(queuedA, a))
                && first.equals(SpikeChecks.vertices(constructedAfterB, a)),
                "Reference cut depends on another entity's submitted/current pose");

        LeggingsView<HumanoidRenderState> left = new LeggingsView<>(source, true);
        LeggingsView<HumanoidRenderState> right = new LeggingsView<>(source, false);
        left.setupAnim(b);
        right.setupAnim(b);
        SpikeChecks.require(SpikeChecks.partVertices(left.root().getChild("waist").getChild("left_ornament")).size() == 24
                && SpikeChecks.partVertices(right.root().getChild("waist").getChild("left_ornament")).isEmpty(),
                "Animated ornament switched source when crossing the waist center");

        for (int age : new int[]{3, 4}) {
            HumanoidRenderState state = new HumanoidRenderState();
            state.ageInTicks = age;
            SpikeChecks.assertModel(source, state);
            left.setupAnim(state);
            ModelPart nativePivot = source.body.getChild("pivot");
            ModelPart viewPivot = left.root().getChild("waist").getChild("pivot");
            SpikeChecks.require(viewPivot != nativePivot && viewPivot.visible == nativePivot.visible
                    && viewPivot.skipDraw == nativePivot.skipDraw && viewPivot.yRot == nativePivot.yRot
                    && viewPivot.getChild("crossing").zScale == nativePivot.getChild("crossing").zScale,
                    "Hierarchy lost native animation/visibility/scale");
        }
        assertTransformedCut(source);
        assertInvalidReferenceRejected();
    }

    private static MeshDefinition mesh() {
        MeshDefinition mesh = HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), CubeDeformation.NONE).legs();
        var body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild("left_ornament", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(4, 6, 0));
        body.addOrReplaceChild("pivot", CubeListBuilder.create(),
                new PartPose(1, 7, 1, 0.2F, 0.4F, -0.3F, 1.1F, 0.8F, 0.9F))
                .addOrReplaceChild("crossing", CubeListBuilder.create().texOffs(5, 8).mirror()
                        .addBox(-4, -1, -2, 8, 2, 4, new CubeDeformation(0.2F)),
                        PartPose.offsetAndRotation(-1, 1, 0, -0.2F, 0.1F, 0.3F));
        return mesh;
    }

    private static void assertTransformedCut(HumanoidModel<?> model) {
        ModelPart part = model.body.getChild("pivot").getChild("crossing");
        // Exercise a non-axis-aligned plane crossing a child face and interpolate in
        // the child's own coordinates. Applying x=0 independently per child is wrong.
        Matrix4f localToWaist = new Matrix4f().translate(1, 7, 1).rotateZYX(-0.3F, 0.4F, 0.2F)
                .scale(1.1F, 0.8F, 0.9F).translate(-1, 1, 0).rotateZYX(0.3F, 0.1F, -0.2F);
        List<ModelPart.Cube> originals = new ArrayList<>();
        part.visit(new PoseStack(), (pose, path, index, cube) -> originals.add(cube));
        for (boolean left : new boolean[]{true, false}) {
            var view = new LeggingsView<>(model, left);
            ModelPart clippedPart = view.root().getChild("waist").getChild("pivot").getChild("crossing");
            clippedPart.visit(new PoseStack(), (pose, path, index, clipped) -> {
                for (ModelPart.Polygon face : clipped.polygons) {
                    for (ModelPart.Vertex vertex : face.vertices()) {
                        float x = localToWaist.transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f()).x;
                        SpikeChecks.require(left ? x >= -0.00002F : x <= 0.00002F, "Hierarchy used the wrong reference plane");
                        if (Math.abs(x) < 0.00002F) {
                            SpikeChecks.require(matchesNativeEdge(originals.getFirst(), vertex, face),
                                    "Transformed hierarchy cut changed UV/normal");
                        }
                    }
                }
            });
        }
    }

    private static boolean matchesNativeEdge(ModelPart.Cube cube, ModelPart.Vertex vertex, ModelPart.Polygon clipped) {
        for (ModelPart.Polygon face : cube.polygons) {
            if (!face.normal().equals(clipped.normal())) {
                continue;
            }
            for (int edge = 0; edge < face.vertices().length; edge++) {
                ModelPart.Vertex a = face.vertices()[edge];
                ModelPart.Vertex b = face.vertices()[(edge + 1) % face.vertices().length];
                Vector3f direction = new Vector3f(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
                Vector3f delta = new Vector3f(vertex.x() - a.x(), vertex.y() - a.y(), vertex.z() - a.z());
                float t = delta.dot(direction) / direction.lengthSquared();
                if (t >= -0.00001F && t <= 1.00001F
                        && new Vector3f(direction).mul(t).distance(delta) < 0.00002F
                        && Math.abs(vertex.u() - (a.u() + (b.u() - a.u()) * t)) < 0.00002F
                        && Math.abs(vertex.v() - (a.v() + (b.v() - a.v()) * t)) < 0.00002F) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertInvalidReferenceRejected() {
        MeshDefinition mesh = mesh();
        mesh.getRoot().getChild("body").addOrReplaceChild("invalid",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, -1, 2, 2, 2),
                new PartPose(0, 0, 0, 0, 0, 0, 0, 1, 1));
        HumanoidModel<HumanoidRenderState> model = new HumanoidModel<>(LayerDefinition.create(mesh, 64, 32).bakeRoot());
        try {
            new LeggingsView<>(model, true);
            throw new AssertionError("Singular reference transform accepted");
        } catch (IllegalArgumentException expected) {
            SpikeChecks.require(expected.getMessage().equals("Invalid reference waist transform"),
                    "Invalid hierarchy failed for an unrelated reason");
        }
    }
}
