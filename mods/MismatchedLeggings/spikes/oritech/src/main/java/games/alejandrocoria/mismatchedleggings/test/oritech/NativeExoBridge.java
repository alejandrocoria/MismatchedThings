package games.alejandrocoria.mismatchedleggings.test.oritech;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.oritech.mixin.SpikeLevelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Modifier;
import java.util.List;

/** Research boundary: raw Gecko generics and reflective state cloning are not a production API. */
public final class NativeExoBridge {
    static final java.util.Map<HumanoidRenderState, net.minecraft.world.entity.LivingEntity> wearers = new java.util.WeakHashMap<>();
    private NativeExoBridge() {
    }

    public static void remember(HumanoidRenderState state, net.minecraft.world.entity.LivingEntity entity) {
        wearers.put(state, entity);
    }

    static boolean exo(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("oritech:exo_leggings");
    }

    public static boolean mixed(EquipmentLayerRenderer renderer, Model<?> model, Object state, ItemStack root,
                         PoseStack pose, SubmitNodeCollector destination, int light, int outline) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null || !(model instanceof HumanoidModel<?>) || !(state instanceof HumanoidRenderState humanoid)
                || !exo(data.left().source()) && !exo(data.right().source())) {
            return false;
        }
        // Both lanes are preflighted before either is committed to the destination.
        var lanes = new java.util.ArrayList<Runnable>();
        for (boolean left : List.of(true, false)) {
            var source = (left ? data.left() : data.right()).source();
            source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
            if (exo(source)) {
                var capture = new GeometryCapture();
                nativePass(source, (HumanoidModel<?>) model, humanoid, pose, capture, light, outline, left);
                if (capture.geometry.isEmpty()) {
                    return false;
                }
                lanes.add(() -> capture.flush(destination));
            } else {
                var capture = new BufferedEquipment();
                renderStandard(renderer, model, humanoid, source, pose, capture, light, outline);
                if (!capture.prepareSplit(left)) {
                    return false;
                }
                lanes.add(() -> capture.flush(destination));
            }
        }
        lanes.forEach(Runnable::run);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderStandard(EquipmentLayerRenderer renderer, Model model, HumanoidRenderState state,
                                       ItemStack source, PoseStack pose, BufferedEquipment capture, int light, int outline) {
        renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state, source,
                pose, capture, light, outline);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void nativePass(ItemStack source, HumanoidModel<?> model, HumanoidRenderState original,
                            PoseStack pose, SubmitNodeCollector collector, int light, int outline, Boolean left) {
        GeoArmorRenderer renderer = GeoRenderProvider.of(source).getGeoArmorRenderer(source, EquipmentSlot.LEGS);
        if (renderer == null) {
            throw new IllegalStateException("Native Exo renderer missing");
        }
        try {
            var state = original.getClass().getConstructor().newInstance();
            for (var field : original.getClass().getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.set(state, field.get(original));
                }
            }
            var wearer = java.util.Objects.requireNonNull(wearers.get(original), "Missing original wearer context");
            var data = new GeoArmorRenderer.RenderData(source.copy(), EquipmentSlot.LEGS, wearer, model);
            renderer.fillRenderState((GeoAnimatable) source.getItem(), data, (GeoRenderState) state, 0);
            // Preserve native captured lighting/overlay rather than replacing them with GUI fullbright.
            state.outlineColor = outline;
            List<RenderPassInfo.BoneUpdater<GeoRenderState>> updates = left == null ? List.of() : List.of((pass, bones) ->
                    bones.ifPresent(left ? "armorRightLeg" : "armorLeftLeg", bone ->
                            bone.skipRender(true).skipChildrenRender(true)));
            renderer.performRenderPass((GeoRenderState) state, pose, collector,
                    ((SpikeLevelAccessor) Minecraft.getInstance().levelRenderer).spikeState().cameraRenderState, updates);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Spike render-state clone failed", failure);
        }
    }
}
