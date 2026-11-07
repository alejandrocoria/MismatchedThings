package games.alejandrocoria.mismatchedleggings.client.compatibility.oritech;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech.LevelRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Optional render-thread-only helper. Block A deliberately does not wire product dispatch. */
public final class OritechEquipmentCapture {
    private static final Identifier ITEM = Identifier.parse("oritech:exo_leggings");
    private static final String RENDERER = "rearth.oritech.client.renderers.ExosuitArmorRenderer";
    private static final Set<String> ARMOR_ANCHORS = Set.of("armorHead", "armorBody", "armorLeftArm", "armorRightArm",
            "armorLeftLeg", "armorRightLeg", "armorLeftBoot", "armorRightBoot", "bipedHead", "bipedBody",
            "bipedLeftArm", "bipedRightArm", "bipedLeftLeg", "bipedRightLeg");
    private static long generation = -1;
    private static boolean disabled;
    private static boolean warned;
    private static boolean extracting;

    private OritechEquipmentCapture() {
    }

    public static boolean extracting() {
        return extracting;
    }

    public static void extract(LivingEntity wearer, HumanoidRenderState target, float partialTick,
                                Supplier<HumanoidRenderState> fresh, Function<HumanoidRenderState, HumanoidModel<?>> rig) {
        var attached = (OritechRenderState) target;
        attached.mismatchedleggings$exoFrame(null);
        refresh();
        var data = MixedLeggingsStructure.validData(target.legsEquipment).orElse(null);
        if (disabled || extracting || data == null) {
            return;
        }
        var sources = List.of(data.left().source(), data.right().source());
        if (sources.stream().noneMatch(OritechEquipmentCapture::isExo)) {
            return;
        }
        extracting = true;
        try {
            var snapshots = new ArrayList<Snapshot>();
            for (var source : sources) {
                if (!isExo(source)) {
                    continue;
                }
                var state = fresh.get();
                if (state == target || !(state instanceof GeoRenderState)) {
                    return;
                }
                var model = rig.apply(state);
                if (model == null) {
                    return;
                }
                source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, target.legsEquipment.hasFoil());
                snapshots.add(snapshot(wearer, source, state, model, partialTick));
            }
            attached.mismatchedleggings$exoFrame(new Frame(target.legsEquipment.copy(), List.copyOf(snapshots), generation));
        } catch (UnsupportedPose unsupported) {
            // A foreign rig/state mismatch belongs to this request, not the optional binding.
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
        } finally {
            extracting = false;
        }
    }

    public static EquipmentCapture capture(ItemStack source, HumanoidRenderState state, PoseStack pose, int outline) {
        refresh();
        if (disabled || !isExo(source) || !(state instanceof OritechRenderState attached)) {
            return null;
        }
        var frame = attached.mismatchedleggings$exoFrame();
        if (frame == null || frame.generation != generation || !ItemStack.matches(frame.root, state.legsEquipment)) {
            return null;
        }
        for (var snapshot : frame.snapshots) {
            // Foil was projected on a defensive source copy, not on provenance.
            var expected = source.copy();
            expected.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, frame.root.hasFoil());
            if (ItemStack.matches(expected, snapshot.source)) {
                return new Capture(snapshot, pose.last().copy(), outline);
            }
        }
        return null;
    }

    private static boolean isExo(ItemStack source) {
        return ITEM.equals(BuiltInRegistries.ITEM.getKey(source.getItem()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Snapshot snapshot(LivingEntity wearer, ItemStack source, HumanoidRenderState state,
                                      HumanoidModel<?> rig, float partialTick) {
        var provider = GeoRenderProvider.of(source);
        GeoArmorRenderer renderer = provider.getGeoArmorRenderer(source, EquipmentSlot.LEGS);
        if (renderer == null || !RENDERER.equals(renderer.getClass().getName()) || !renderer.getRenderLayers().isEmpty()) {
            throw new IllegalArgumentException("Unsupported Oritech armor renderer/layers");
        }
        ExoPose frozen;
        try {
            frozen = ExoPose.freeze(rig, state);
        } catch (IllegalArgumentException | ClassCastException unsupported) {
            throw new UnsupportedPose(unsupported);
        }
        var data = new GeoArmorRenderer.RenderData(source.copy(), EquipmentSlot.LEGS, wearer, frozen);
        renderer.fillRenderState((GeoAnimatable) source.getItem(), data, (GeoRenderState) state, partialTick);
        validate(renderer, state);
        return new Snapshot(source.copy(), state, renderer, generation);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void validate(GeoArmorRenderer renderer, HumanoidRenderState state) {
        if (!renderer.getSegmentsForSlot(state, EquipmentSlot.LEGS).equals(List.of(
                GeoArmorRenderer.ArmorSegment.LEFT_LEG, GeoArmorRenderer.ArmorSegment.RIGHT_LEG))
                || !"armorLeftLeg".equals(renderer.getBoneNameForSegment(state, GeoArmorRenderer.ArmorSegment.LEFT_LEG))
                || !"armorRightLeg".equals(renderer.getBoneNameForSegment(state, GeoArmorRenderer.ArmorSegment.RIGHT_LEG))) {
            throw new IllegalArgumentException("Unsupported Oritech leggings segments");
        }
        var model = renderer.getGeoModel();
        var baked = model.getBakedModel(model.getModelResource((GeoRenderState) state));
        if (baked.isMissingno()) {
            throw new IllegalArgumentException("Missing Oritech armor geometry");
        }
        for (String name : List.of("armorLeftLeg", "armorRightLeg")) {
            GeoBone bone = baked.getBone(name).orElseThrow(() -> new IllegalArgumentException("Missing Exo leg bone: " + name));
            validateChildren(bone);
        }
    }

    private static void validateChildren(GeoBone bone) {
        for (var child : bone.children()) {
            if (ARMOR_ANCHORS.contains(child.name())) {
                throw new IllegalArgumentException("Cross-slot Exo leg descendant: " + child.name());
            }
            validateChildren(child);
        }
    }

    private static void refresh() {
        long current = LeggingsGeometryCache.statistics().generation();
        if (generation != current) {
            generation = current;
            disabled = false;
        }
    }

    private static void fail(Throwable failure) {
        disabled = true;
        if (!warned) {
            warned = true;
            LeggingsContent.LOG.warn("Cannot capture Oritech Exo leggings; using mixed leggings fallback", failure);
        }
    }

    public static final class Frame {
        private final ItemStack root;
        private final List<Snapshot> snapshots;
        private final long generation;

        private Frame(ItemStack root, List<Snapshot> snapshots, long generation) {
            this.root = root;
            this.snapshots = snapshots;
            this.generation = generation;
        }
    }

    private static final class UnsupportedPose extends RuntimeException {
        UnsupportedPose(RuntimeException cause) {
            super(cause);
        }
    }

    @SuppressWarnings("rawtypes")
    private record Snapshot(ItemStack source, HumanoidRenderState state, GeoArmorRenderer renderer, long generation) {
    }

    private static final class Capture implements EquipmentCapture {
        private final Snapshot snapshot;
        private final PoseStack.Pose pose;
        private final int outline;
        private ExoGeometryBuffer prepared;

        Capture(Snapshot snapshot, PoseStack.Pose pose, int outline) {
            this.snapshot = snapshot;
            this.pose = pose;
            this.outline = outline;
        }

        @Override
        public boolean prepareSplit(boolean left) {
            return prepare(left);
        }

        @Override
        public boolean prepareFull() {
            return prepare(null);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private boolean prepare(Boolean left) {
            prepared = null;
            refresh();
            if (disabled || snapshot.generation != generation) {
                return false;
            }
            try {
                var state = snapshot.state;
                state.outlineColor = outline;
                var renderer = snapshot.renderer;
                validate(renderer, state);
                var material = renderer.getRenderType(state, renderer.getTextureLocation((GeoRenderState) state));
                var buffer = new ExoGeometryBuffer(material);
                var stack = new PoseStack();
                stack.last().set(pose);
                List<RenderPassInfo.BoneUpdater<GeoRenderState>> masks = left == null ? List.of() : List.of((pass, bones) ->
                        bones.ifPresent(left ? "armorRightLeg" : "armorLeftLeg", bone -> bone.skipRender(true).skipChildrenRender(true)));
                renderer.performRenderPass((GeoRenderState) state, stack, buffer,
                        ((LevelRenderStateAccessor) Minecraft.getInstance().levelRenderer).mismatchedleggings$levelState().cameraRenderState, masks);
                if (!buffer.valid()) {
                    throw new IllegalArgumentException("Unsupported Oritech geometry submission");
                }
                prepared = buffer;
                return true;
            } catch (RuntimeException | LinkageError failure) {
                fail(failure);
                return false;
            }
        }

        @Override
        public void flush(SubmitNodeCollector destination) {
            if (prepared == null || snapshot.generation != LeggingsGeometryCache.statistics().generation()) {
                throw new IllegalStateException("Oritech capture is unprepared or stale");
            }
            prepared.flush(destination);
        }
    }
}
