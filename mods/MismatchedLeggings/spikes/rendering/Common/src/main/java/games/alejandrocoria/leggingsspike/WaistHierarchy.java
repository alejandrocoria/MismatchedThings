package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clips in reference waist space, but keeps geometry and animation in each native child's local space. */
final class WaistHierarchy {
    private final List<Binding> bindings = new ArrayList<>();
    final ModelPart root;

    WaistHierarchy(ModelPart source, boolean left) {
        Node tree = new Node(source, new Matrix4f());
        source.visit(new PoseStack(), (pose, path, index, cube) -> {
            Node node = tree;
            if (!path.isEmpty()) {
                for (String name : path.substring(1).split("/")) {
                    Node parent = node;
                    node = parent.children.computeIfAbsent(name, ignored -> {
                        ModelPart child = parent.source.getChild(name);
                        return new Node(child, referenceTransform(parent.reference, child.getInitialPose()));
                    });
                }
            }
            node.cubes.addAll(WaistClipper.clip(cube, left, node.reference));
        });
        root = build(tree);
    }

    void copyLiveTransforms() {
        for (Binding binding : bindings) {
            LeggingsView.copyTransform(binding.source(), binding.view());
        }
    }

    private ModelPart build(Node node) {
        Map<String, ModelPart> children = new LinkedHashMap<>();
        node.children.forEach((name, child) -> children.put(name, build(child)));
        ModelPart view = new ModelPart(node.cubes, children);
        bindings.add(new Binding(node.source, view));
        return view;
    }

    private static Matrix4f referenceTransform(Matrix4f parent, PartPose pose) {
        // Positions here are model pixels, matching Cube vertices, not renderer world units.
        // Do not include the waist's own crouch/stand pose in the ownership plane.
        return new Matrix4f(parent).translate(pose.x(), pose.y(), pose.z())
                .rotateZYX(pose.zRot(), pose.yRot(), pose.xRot())
                .scale(pose.xScale(), pose.yScale(), pose.zScale());
    }

    private static final class Node {
        private final ModelPart source;
        private final Matrix4f reference;
        private final List<ModelPart.Cube> cubes = new ArrayList<>();
        private final Map<String, Node> children = new LinkedHashMap<>();

        private Node(ModelPart source, Matrix4f reference) {
            this.source = source;
            this.reference = reference;
        }
    }

    private record Binding(ModelPart source, ModelPart view) {
    }
}
