package games.alejandrocoria.mismatchedleggings.client.compatibility.betterend;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Optional, render-thread-only bridge for the inspected Crystalite leggings renderer. */
public final class CrystaliteEquipmentCapture {
    private static final Identifier ITEM = Identifier.parse("betterend:crystalite_leggings");
    private static final String RENDERER = "org.betterx.betterend.item.model.CrystaliteArmorRenderer";
    private static final String MODEL = "org.betterx.betterend.item.model.CrystaliteLeggingsModel";
    private static long generation = -1;
    private static ArmorRenderer owner;
    private static CrystaliteModel model;
    private static List<RenderType> materials;
    private static boolean disabled;
    private static boolean reportedFailure;
    private static boolean reportedPoseFailure;

    private CrystaliteEquipmentCapture() {
    }

    /** Returns unprepared submissions, or null without touching the destination or item data. */
    public static BufferedEquipment capture(ItemStack source, Model<?> rig, Object state, boolean foil,
                                            PoseStack pose, int light, int outline) {
        var equippable = source.get(DataComponents.EQUIPPABLE);
        if (!ITEM.equals(BuiltInRegistries.ITEM.getKey(source.getItem())) || equippable == null
                || equippable.slot() != EquipmentSlot.LEGS || !(rig instanceof HumanoidModel<?> humanoid)
                || !(state instanceof HumanoidRenderState humanoidState)) {
            return null;
        }

        refreshEpoch();
        if (disabled) {
            return null;
        }
        try {
            var renderer = ArmorRendererRegistryImpl.get(source.getItem());
            if (renderer == null) {
                // Fabric materializes registered factories during resource loading, not registration.
                return null;
            }
            if (!RENDERER.equals(renderer.getClass().getName())) {
                return null;
            }
            if (owner != renderer) {
                var geometry = geometry(renderer);
                var layers = probe(renderer, source, geometry);
                model = new CrystaliteModel(geometry);
                materials = layers;
                owner = renderer;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disabled = true;
            owner = null;
            model = null;
            materials = null;
            if (!reportedFailure) {
                reportedFailure = true;
                LeggingsContent.LOG.warn("Cannot capture Better End Crystalite armor; using mixed leggings fallback", failure);
            }
            return null;
        }

        CrystaliteModel.Pose frozen;
        try {
            frozen = freeze(humanoid, humanoidState);
        } catch (RuntimeException | LinkageError failure) {
            // A request-specific rig/state mismatch must not disable ordinary humanoids this epoch.
            if (!reportedPoseFailure) {
                reportedPoseFailure = true;
                LeggingsContent.LOG.warn("Cannot capture this Crystalite armor pose; using mixed leggings fallback", failure);
            }
            return null;
        }

        var captured = new BufferedEquipment();
        for (var material : materials) {
            captured.submitModel(model, frozen, pose, material, light,
                    OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
        if (foil) {
            captured.submitModel(model, frozen, pose, RenderTypes.armorEntityGlint(), light,
                    OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
        return captured;
    }

    private static void refreshEpoch() {
        long current = LeggingsGeometryCache.statistics().generation();
        if (generation != current) {
            generation = current;
            owner = null;
            model = null;
            materials = null;
            disabled = false;
        }
    }

    private static HumanoidModel<?> geometry(ArmorRenderer renderer) throws ReflectiveOperationException {
        var type = Class.forName(MODEL, true, renderer.getClass().getClassLoader());
        var factory = type.getMethod("createModel", EntityModelSet.class);
        if (!Modifier.isStatic(factory.getModifiers()) || !HumanoidModel.class.isAssignableFrom(factory.getReturnType())) {
            throw new IllegalArgumentException("Unsupported Crystalite model factory");
        }
        var result = factory.invoke(null, (Object) null);
        if (!(result instanceof HumanoidModel<?> geometry) || result.getClass() != type) {
            throw new IllegalArgumentException("Unexpected Crystalite factory model");
        }

        Set<ModelPart> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var part : List.of(geometry.body, geometry.rightLeg, geometry.leftLeg)) {
            if (part.getAllParts().stream().allMatch(ModelPart::isEmpty)) {
                throw new IllegalArgumentException("Crystalite model is missing body/leg geometry");
            }
            part.getAllParts().forEach(selected::add);
            // Match native ArmorParts detachment, only on the factory model owned by this bridge.
            part.setInitialPose(PartPose.ZERO);
            part.resetPose();
        }
        if (!geometry.root().isEmpty() || geometry.root().getAllParts().stream()
                .anyMatch(part -> !part.isEmpty() && !selected.contains(part))) {
            throw new IllegalArgumentException("Crystalite model has geometry outside its body and legs");
        }
        return geometry;
    }

    @SuppressWarnings("unchecked")
    private static CrystaliteModel.Pose freeze(HumanoidModel<?> rig, HumanoidRenderState state) {
        ((HumanoidModel<HumanoidRenderState>) rig).setupAnim(state);
        return new CrystaliteModel.Pose(rig);
    }

    @SuppressWarnings("unchecked")
    private static List<RenderType> probe(ArmorRenderer renderer, ItemStack source, HumanoidModel<?> geometry) {
        var rig = (HumanoidModel<HumanoidRenderState>) geometry;
        rig.root().visible = true;
        rig.body.visible = true;
        rig.rightLeg.visible = true;
        rig.leftLeg.visible = true;
        rig.setupAnim(new HumanoidRenderState());

        var probe = new MaterialProbe();
        renderer.render(new PoseStack(), probe, source.copy(), new HumanoidRenderState(), EquipmentSlot.LEGS, 0, rig);
        return probe.materials();
    }

    /** Accept only the inspected two native layers; no unrecognized submissions may escape. */
    private static final class MaterialProbe extends SubmitNodeStorage {
        private final List<RenderType> calls = new ArrayList<>();
        private final List<SubmitNodeCollection> collections = new ArrayList<>();
        private boolean unexpectedOrder;

        @Override
        public SubmitNodeCollection order(int order) {
            unexpectedOrder |= order != 0;
            var collection = new SubmitNodeCollection(this) {
                @Override
                public void submitCustomGeometry(PoseStack pose, RenderType type,
                                                 SubmitNodeCollector.CustomGeometryRenderer renderer) {
                    if (calls.size() >= 6 || type == null || renderer == null) {
                        throw new IllegalArgumentException("Unsupported Crystalite material submission");
                    }
                    calls.add(type);
                }
            };
            collections.add(collection);
            return collection;
        }

        List<RenderType> materials() {
            if (unexpectedOrder || collections.stream().anyMatch(SubmitNodeCollection::wasUsed)
                    || calls.size() != 6 || calls.get(0) == calls.get(3)
                    || calls.subList(0, 3).stream().anyMatch(type -> type != calls.get(0))
                    || calls.subList(3, 6).stream().anyMatch(type -> type != calls.get(3))) {
                throw new IllegalArgumentException("Unsupported Crystalite two-layer/three-part material contract");
            }
            return List.of(calls.get(0), calls.get(3));
        }
    }
}
