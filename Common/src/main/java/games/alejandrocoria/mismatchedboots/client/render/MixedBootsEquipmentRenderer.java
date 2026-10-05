package games.alejandrocoria.mismatchedboots.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

public final class MixedBootsEquipmentRenderer {
    private MixedBootsEquipmentRenderer() {
    }

    public static <S> boolean renderLayers(
            EquipmentLayerRenderer renderer,
            EquipmentClientInfo.LayerType layerType,
            Model<? super S> model,
            S state,
            ItemStack stack,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int outlineColor) {
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        if (data == null || !(model instanceof HumanoidModel<?> humanoidModel)) {
            return false;
        }

        ItemStack left = MixedBootsItemRenderer.sourceView(data.left().source(), stack.hasFoil());
        ItemStack right = MixedBootsItemRenderer.sourceView(data.right().source(), stack.hasFoil());
        ResourceKey<EquipmentAsset> leftAsset = standardFeetAsset(left);
        ResourceKey<EquipmentAsset> rightAsset = standardFeetAsset(right);
        if (leftAsset == null || rightAsset == null) {
            return false;
        }

        renderer.renderLayers(
                layerType,
                leftAsset,
                sideModel(model, humanoidModel.leftLeg, Side.LEFT),
                state,
                left,
                poseStack,
                submitNodeCollector,
                lightCoords,
                outlineColor);
        renderer.renderLayers(
                layerType,
                rightAsset,
                sideModel(model, humanoidModel.rightLeg, Side.RIGHT),
                state,
                right,
                poseStack,
                submitNodeCollector,
                lightCoords,
                outlineColor);
        return true;
    }

    private static ResourceKey<EquipmentAsset> standardFeetAsset(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.FEET) {
            return null;
        }
        return equippable.assetId().orElse(null);
    }

    /** Reapplies our side ownership after a loader extension resolves its final armor model. */
    public static <S> Model<? super S> preserveSplit(
            Model<? super S> original, Model<? super S> resolved) {
        if (original == resolved || !(original instanceof SplitModel split)
                || !(resolved instanceof HumanoidModel<?> humanoid)) {
            return resolved;
        }
        // Structural views remain safe when cached models are queued for both feet/entities.
        // Never toggle visibility here: submission and setupAnim happen at different times.
        Side side = split.mismatchedboots$side();
        return sideModel(resolved, side == Side.LEFT ? humanoid.leftLeg : humanoid.rightLeg, side);
    }

    private static <S> Model<? super S> sideModel(
            Model<? super S> source, ModelPart leg, Side side) {
        return new SideModel<>(source, leg, side);
    }

    enum Side {
        LEFT,
        RIGHT
    }

    interface SplitModel {
        Side mismatchedboots$side();
    }

    private static final class SideModel<S> extends Model<S> implements SplitModel {
        private final Model<? super S> source;
        private final Side side;

        private SideModel(Model<? super S> source, ModelPart leg, Side side) {
            super(leg, source.renderType());
            this.source = source;
            this.side = side;
        }

        @Override
        public void setupAnim(S state) {
            source.setupAnim(state);
        }

        @Override
        public Side mismatchedboots$side() {
            return side;
        }
    }
}
