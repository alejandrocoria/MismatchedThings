package games.alejandrocoria.crystalitespike;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.List;
import java.util.Map;

/** Stable native geometry; each queued submission carries an independent frozen rig pose. */
public final class CrystaliteModel extends HumanoidModel<HumanoidRenderState> {
    public CrystaliteModel(HumanoidModel<?> nativeGeometry) {
        super(nativeGeometry.root());
    }

    public static Pose freeze(HumanoidModel<?> rig) {
        return new Pose(rig);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        if (!(state instanceof Pose pose)) {
            throw new IllegalArgumentException("Expected frozen Crystalite rig pose");
        }
        LeggingsView.copyTransform(pose.root, root());
        copyBone(pose.body, body);
        copyBone(pose.left, leftLeg);
        copyBone(pose.right, rightLeg);
    }

    private static void copyBone(ModelPart from, ModelPart to) {
        LeggingsView.copyTransform(from, to);
        // Native ArmorParts checks bone visibility, but ignores bone skipDraw when attaching geometry.
        to.skipDraw = false;
    }

    public static final class Pose extends HumanoidRenderState {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart left;
        private final ModelPart right;

        private Pose(HumanoidModel<?> rig) {
            root = freezePart(rig.root());
            body = freezePart(rig.body);
            left = freezePart(rig.leftLeg);
            right = freezePart(rig.rightLeg);
        }

        private static ModelPart freezePart(ModelPart source) {
            var snapshot = new ModelPart(List.of(), Map.of());
            LeggingsView.copyTransform(source, snapshot);
            return snapshot;
        }
    }
}
