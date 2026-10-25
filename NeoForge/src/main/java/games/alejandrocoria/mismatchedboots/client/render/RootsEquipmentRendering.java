package games.alejandrocoria.mismatchedboots.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;

/** Roots boots hide the legs and animate independent top-level feet instead. */
public final class RootsEquipmentRendering {
    private RootsEquipmentRendering() {
    }

    public static <S> Model<? super S> preserveSplit(Model<? super S> original, Model<? super S> resolved) {
        if (resolved instanceof HumanoidModel<?> && supportedModel(resolved)
                && resolved.root().hasChild("left_foot") && resolved.root().hasChild("right_foot")) {
            // Select geometry only; native setupAnim must retain ownership of cached pose/visibility.
            return MixedBootsEquipmentRenderer.preserveSplit(original, resolved,
                                                             resolved.root().getChild("left_foot"), resolved.root().getChild("right_foot"));
        }
        return MixedBootsEquipmentRenderer.preserveSplit(original, resolved);
    }

    private static boolean supportedModel(Model<?> model) {
        return switch (model.getClass().getName()) {
            case "elucent.rootsclassic.client.model.SylvanArmorModel",
                 "elucent.rootsclassic.client.model.WildwoodArmorModel" -> true;
            default -> false;
        };
    }
}
