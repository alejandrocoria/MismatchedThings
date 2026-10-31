package games.alejandrocoria.mismatchedleggings.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable local geometry. Live parts/animation bindings belong only to a queued view. */
final class WaistGeometry {
    private final Template template;

    WaistGeometry(ModelPart source, boolean left) {
        Builder tree = new Builder(source, new Matrix4f());
        source.visit(new PoseStack(), (pose, path, index, cube) -> {
            Builder node = tree;
            if (!path.isEmpty()) {
                for (String name : path.substring(1).split("/")) {
                    Builder parent = node;
                    node = parent.children.computeIfAbsent(name, ignored -> {
                        ModelPart child = parent.source.getChild(name);
                        return new Builder(child, referenceTransform(parent.reference, child.getInitialPose()));
                    });
                }
            }
            node.cubes.addAll(WaistClipper.clip(cube, left, node.reference));
        });
        template = freeze(tree);
    }

    Bound bind(ModelPart source) {
        List<Binding> bindings = new ArrayList<>();
        ModelPart root = bind(template, source, bindings);
        return new Bound(root, List.copyOf(bindings));
    }

    private static ModelPart bind(Template template, ModelPart source, List<Binding> bindings) {
        Map<String, ModelPart> children = new LinkedHashMap<>();
        template.children().forEach((name, child) -> children.put(name, bind(child, source.getChild(name), bindings)));
        ModelPart view = new ModelPart(template.cubes(), children);
        bindings.add(new Binding(source, view));
        return view;
    }

    private static Template freeze(Builder builder) {
        Map<String, Template> children = new LinkedHashMap<>();
        builder.children.forEach((name, child) -> children.put(name, freeze(child)));
        return new Template(List.copyOf(builder.cubes), java.util.Collections.unmodifiableMap(children));
    }

    private static Matrix4f referenceTransform(Matrix4f parent, PartPose pose) {
        // Cube vertices use model pixels. Exclude the waist's animated crouch/body pose:
        // even a child crossing the center must retain its reference-side ownership.
        return new Matrix4f(parent).translate(pose.x(), pose.y(), pose.z())
                .rotateZYX(pose.zRot(), pose.yRot(), pose.xRot())
                .scale(pose.xScale(), pose.yScale(), pose.zScale());
    }

    record Bound(ModelPart root, List<Binding> bindings) {
        void copyLiveTransforms() {
            for (Binding binding : bindings) {
                LeggingsView.copyTransform(binding.source(), binding.view());
            }
        }
    }

    private record Binding(ModelPart source, ModelPart view) {}

    private record Template(List<ModelPart.Cube> cubes, Map<String, Template> children) {}

    private static final class Builder {
        private final ModelPart source;
        private final Matrix4f reference;
        private final List<ModelPart.Cube> cubes = new ArrayList<>();
        private final Map<String, Builder> children = new LinkedHashMap<>();

        private Builder(ModelPart source, Matrix4f reference) {
            this.source = source;
            this.reference = reference;
        }
    }
}
