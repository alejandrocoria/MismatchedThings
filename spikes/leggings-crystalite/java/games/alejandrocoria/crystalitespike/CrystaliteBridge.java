package games.alejandrocoria.crystalitespike;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Deliberately bounded prototype, not a generic Fabric ArmorRenderer adapter. */
public final class CrystaliteBridge {
    public static final Component BASELINE = Component.literal("spike baseline");
    private static final Identifier ID = Identifier.parse("betterend:crystalite_leggings");
    public static EquipmentLayerRenderer equipmentRenderer;
    public static boolean forceFailure;
    public static long adapted;
    private static HumanoidModel<?> geometry;
    private static CrystaliteModel model;
    private static List<RenderType> layers;

    private CrystaliteBridge() {
    }

    public static ItemStack nativeStack() {
        return BuiltInRegistries.ITEM.getValue(ID).getDefaultInstance();
    }

    public static boolean crystalite(ItemStack stack) {
        return ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static void reset() {
        geometry = null;
        model = null;
        layers = null;
    }

    public static HumanoidModel<?> geometry() {
        if (geometry == null) {
            try {
                geometry = (HumanoidModel<?>) Class.forName("org.betterx.betterend.item.model.CrystaliteLeggingsModel")
                        .getMethod("createModel", EntityModelSet.class).invoke(null, (Object) null);
                for (var part : List.of(geometry.body, geometry.rightLeg, geometry.leftLeg)) {
                    // Same detach/reset used by Better End's private CrystaliteArmorParts, on our own model.
                    part.setInitialPose(PartPose.ZERO);
                    part.resetPose();
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        return geometry;
    }

    public static CrystaliteModel model() {
        if (model == null) {
            model = new CrystaliteModel(geometry());
        }
        return model;
    }

    public static NativeCapture nativeCapture(ItemStack stack, HumanoidRenderState state,
                                               HumanoidModel<HumanoidRenderState> rig) {
        ArmorRenderer nativeRenderer = ArmorRendererRegistryImpl.get(stack.getItem());
        if (nativeRenderer == null || !nativeRenderer.getClass().getName()
                .equals("org.betterx.betterend.item.model.CrystaliteArmorRenderer")) {
            throw new IllegalArgumentException("Unexpected Crystalite renderer");
        }
        rig.setupAnim(state);
        var capture = new NativeCapture();
        nativeRenderer.render(new PoseStack(), capture, stack, state, EquipmentSlot.LEGS, 15728880, rig);
        return capture;
    }

    @SuppressWarnings("unchecked")
    private static List<RenderType> layers() {
        if (layers == null) {
            var probe = (HumanoidModel<HumanoidRenderState>) geometry();
            probe.root().visible = true;
            probe.body.visible = true;
            probe.leftLeg.visible = true;
            probe.rightLeg.visible = true;
            // Probe a known visible rig, not the first entity's possibly hidden parts.
            var calls = nativeCapture(nativeStack(), new HumanoidRenderState(), probe).calls;
            if (calls.size() != 6 || calls.get(0).type() == calls.get(3).type()
                    || calls.subList(0, 3).stream().anyMatch(call -> call.type() != calls.get(0).type())
                    || calls.subList(3, 6).stream().anyMatch(call -> call.type() != calls.get(3).type())) {
                throw new IllegalArgumentException("Unexpected Crystalite two-layer/three-part contract");
            }
            layers = List.of(calls.get(0).type(), calls.get(3).type());
        }
        return layers;
    }

    public static boolean render(EquipmentLayerRenderer renderer, HumanoidModel<HumanoidRenderState> rig,
                                  HumanoidRenderState state, EquipmentClientInfo.LayerType layer,
                                  ItemStack root, PoseStack pose, SubmitNodeCollector output, int light, int outline) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null || BASELINE.equals(root.getCustomName())
                || !(crystalite(data.left().source()) || crystalite(data.right().source()))) {
            return false;
        }
        var left = capture(renderer, rig, state, layer, data.left().source(), root.hasFoil(), pose, light, outline);
        if (left == null || !left.prepareSplit(true) || forceFailure) {
            return false;
        }
        var right = capture(renderer, rig, state, layer, data.right().source(), root.hasFoil(), pose, light, outline);
        if (right == null || !right.prepareSplit(false)) {
            return false;
        }
        left.flush(output);
        right.flush(output);
        adapted++;
        return true;
    }

    private static BufferedEquipment capture(EquipmentLayerRenderer renderer, HumanoidModel<HumanoidRenderState> rig,
                                               HumanoidRenderState state, EquipmentClientInfo.LayerType layer,
                                               ItemStack source, boolean foil, PoseStack pose, int light, int outline) {
        var result = new BufferedEquipment();
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        if (!crystalite(source)) {
            var equipment = source.get(DataComponents.EQUIPPABLE);
            if (!EquipmentSources.INSTANCE.usesStandardRenderer(source) || equipment == null || equipment.assetId().isEmpty()) {
                return null;
            }
            renderer.renderLayers(layer, equipment.assetId().orElseThrow(), rig, state, source, pose, result, light, outline);
            return result;
        }
        var nativeRenderer = ArmorRendererRegistryImpl.get(source.getItem());
        if (nativeRenderer == null || !nativeRenderer.getClass().getName()
                .equals("org.betterx.betterend.item.model.CrystaliteArmorRenderer")) {
            return null;
        }
        rig.setupAnim(state);
        var frozen = CrystaliteModel.freeze(rig);
        var materials = layers();
        for (var type : materials) {
            result.submitModel(model(), frozen, pose, type, light,
                    OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
        if (foil) {
            result.submitModel(model(), frozen, pose, RenderTypes.armorEntityGlint(), light,
                    OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
        return result;
    }

    public static final class NativeCapture extends SubmitNodeStorage {
        public final List<NativeCall> calls = new ArrayList<>();

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            calls.add(new NativeCall(pose.last().copy(), type, renderer));
        }
    }

    public record NativeCall(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}
}
