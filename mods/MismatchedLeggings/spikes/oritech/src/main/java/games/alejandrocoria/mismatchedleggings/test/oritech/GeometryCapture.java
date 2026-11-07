package games.alejandrocoria.mismatchedleggings.test.oritech;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

final class GeometryCapture extends SubmitNodeStorage {
    final List<Node> geometry = new ArrayList<>();

    @Override
    public SubmitNodeCollection order(int order) {
        return new SubmitNodeCollection(this) {
            @Override
            public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
                geometry.add(new Node(order, pose.last().copy(), type, renderer));
            }
        };
    }

    void flush(SubmitNodeCollector destination) {
        for (var node : geometry) {
            var pose = new PoseStack();
            pose.last().set(node.pose());
            destination.order(node.order()).submitCustomGeometry(pose, node.type(), node.renderer());
        }
    }

    record Node(int order, PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
    }
}
