package games.alejandrocoria.mismatchedleggings.client.compatibility.oritech;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

/** Only the inspected single native armor geometry pass is accepted, never silently dropped work. */
final class ExoGeometryBuffer extends SubmitNodeStorage {
    private final RenderType expectedMaterial;
    private final List<SubmitNodeCollection> collections = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();
    private boolean unexpectedOrder;
    private boolean rejected;

    ExoGeometryBuffer(RenderType expectedMaterial) {
        this.expectedMaterial = expectedMaterial;
    }

    @Override
    public SubmitNodeCollection order(int order) {
        unexpectedOrder |= order != 0;
        var collection = new SubmitNodeCollection(this) {
            @Override
            public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
                if (type != expectedMaterial || renderer == null || !nodes.isEmpty()) {
                    rejected = true;
                    throw new IllegalArgumentException("Unsupported Oritech armor geometry/material pass");
                }
                nodes.add(new Node(pose.last().copy(), type, renderer));
            }
        };
        collections.add(collection);
        return collection;
    }

    boolean valid() {
        return !rejected && !unexpectedOrder && nodes.size() == 1 && collections.stream().noneMatch(SubmitNodeCollection::wasUsed);
    }

    void flush(SubmitNodeCollector destination) {
        if (!valid()) {
            throw new IllegalStateException("Oritech geometry requires successful preflight");
        }
        var node = nodes.getFirst();
        var pose = new PoseStack();
        pose.last().set(node.pose());
        destination.order(0).submitCustomGeometry(pose, node.type(), node.renderer());
    }

    private record Node(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
    }
}
