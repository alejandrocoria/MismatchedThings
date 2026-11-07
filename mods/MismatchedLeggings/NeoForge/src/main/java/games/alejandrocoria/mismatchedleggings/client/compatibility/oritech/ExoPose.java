package games.alejandrocoria.mismatchedleggings.client.compatibility.oritech;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native Gecko only reads these part transforms; it must never animate a borrowed rig later. */
final class ExoPose extends HumanoidModel<HumanoidRenderState> {
    private static final List<String> PARTS = List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg");

    private ExoPose(HumanoidModel<?> source) {
        super(emptyRoot());
        LeggingsView.copyTransform(source.root(), root());
        for (String name : PARTS) {
            LeggingsView.copyTransform(source.root().getChild(name), root().getChild(name));
        }
        LeggingsView.copyTransform(source.hat, hat);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        // Oritech calls setupAnim during deferred bone adjustment. The native pose is already frozen.
    }

    @SuppressWarnings("unchecked")
    static ExoPose freeze(HumanoidModel<?> rig, HumanoidRenderState state) {
        var parts = rig.root().getAllParts();
        var before = parts.stream().map(ExoPose::snapshot).toList();
        try {
            ((HumanoidModel<HumanoidRenderState>) rig).setupAnim(state);
            return new ExoPose(rig);
        } finally {
            for (int index = 0; index < parts.size(); index++) {
                LeggingsView.copyTransform(before.get(index), parts.get(index));
            }
        }
    }

    private static ModelPart snapshot(ModelPart source) {
        var copy = new ModelPart(List.of(), Map.of());
        LeggingsView.copyTransform(source, copy);
        return copy;
    }

    private static ModelPart emptyRoot() {
        var children = new LinkedHashMap<String, ModelPart>();
        PARTS.forEach(name -> children.put(name, new ModelPart(List.of(), Map.of())));
        children.put("head", new ModelPart(List.of(), Map.of("hat", new ModelPart(List.of(), Map.of()))));
        return new ModelPart(List.of(), children);
    }
}
