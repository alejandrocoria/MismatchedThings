package games.alejandrocoria.mismatchedleggings.client.compatibility.betterend;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.List;
import java.util.Map;

/** Stable owned geometry; queued entities share no mutable animation state. */
final class CrystaliteModel extends HumanoidModel<HumanoidRenderState> {
    CrystaliteModel(HumanoidModel<?> geometry) {
        super(geometry.root());
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        if (!(state instanceof Pose pose)) {
            throw new IllegalArgumentException("Expected a frozen Crystalite armor pose");
        }
        LeggingsView.copyTransform(pose.root, root());
        copyBone(pose.body, body);
        copyBone(pose.left, leftLeg);
        copyBone(pose.right, rightLeg);
    }

    private static void copyBone(ModelPart from, ModelPart to) {
        LeggingsView.copyTransform(from, to);
        // Native detached attachments honor anchor visibility, but not its skipDraw flag.
        to.skipDraw = false;
    }

    static final class Pose extends HumanoidRenderState {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart left;
        private final ModelPart right;

        Pose(HumanoidModel<?> rig) {
            root = freeze(rig.root());
            body = freeze(rig.body);
            left = freeze(rig.leftLeg);
            right = freeze(rig.rightLeg);
        }

        private static ModelPart freeze(ModelPart source) {
            var snapshot = new ModelPart(List.of(), Map.of());
            LeggingsView.copyTransform(source, snapshot);
            return snapshot;
        }
    }
}
