package games.alejandrocoria.crystalitespike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class SpikeChecks {
    private SpikeChecks() {
    }

    @SuppressWarnings("unchecked")
    static String run(Minecraft client) {
        var models = client.getEntityModels();
        var avatar = new AvatarRenderState();
        avatar.entityType = EntityType.PLAYER;
        avatar.isCrouching = true;
        avatar.walkAnimationPos = 1.4F;
        avatar.walkAnimationSpeed = 0.7F;
        compare((HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>) new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), avatar);
        compare((HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>) new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM_ARMOR.legs()), true), avatar);
        avatar.isCrouching = false;
        avatar.isPassenger = true;
        compare((HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>) new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), avatar);
        var baby = new HumanoidRenderState();
        baby.isBaby = true;
        baby.entityType = EntityType.ZOMBIE;
        baby.walkAnimationPos = 2;
        baby.walkAnimationSpeed = 0.6F;
        compare(new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_BABY_ARMOR.legs())), baby);
        var posed = new ArmorStandRenderState();
        posed.bodyPose = new Rotations(13, 25, -10);
        posed.leftLegPose = new Rotations(-40, 15, -8);
        posed.rightLegPose = new Rotations(25, -12, 15);
        compare((HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>) new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs())), posed);
        posed.isSmall = true;
        posed.isBaby = true;
        compare((HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>) new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_SMALL_ARMOR.legs())), posed);

        var rig = new HumanoidModel<HumanoidRenderState>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        var state = new HumanoidRenderState();
        submissions(rig, state);
        visibility(rig, state);
        queueAndCache(rig, state);
        return "native.geometryUvNormalsColorsLight=true\nwaist.complementaryAreaAndSelectedLeg=true\n"
                + "poses.regularSlimCrouchSeatedBabyPosedSmall=true\nmaterials.nativeTwoLayers=true\n"
                + "fallback.ownerNeutralUnknownAtomic=true\nrootFoil.additionalPass=true\n"
                + "native.foilAndTrimIgnored=true\nvisibility.nativeBoneRules=true\nqueue.independent=true\ncache.twoTemplates=true\n";
    }

    private static void compare(HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        var nativeCalls = CrystaliteBridge.nativeCapture(CrystaliteBridge.nativeStack(), state, rig).calls;
        require(nativeCalls.size() == 6, "Native Crystalite did not emit two layers of three parts");
        var reference = new ArrayList<Vertex>();
        for (var call : nativeCalls.subList(0, 3)) {
            reference.addAll(vertices(call));
        }
        var model = CrystaliteBridge.model();
        var frozen = CrystaliteModel.freeze(rig);
        var full = vertices(model, frozen);
        require(keys(reference).equals(keys(full)), "Public native geometry/rig differs from actual native callbacks: " + rig.getClass());
        var left = vertices(new LeggingsView<>(model, true), frozen);
        var right = vertices(new LeggingsView<>(model, false), frozen);
        require(!left.isEmpty() && !right.isEmpty() && Math.abs(area(full) - area(left) - area(right)) < 0.00001,
                "Clipped Crystalite waist/legs lost or duplicated surface");
        var snapshot = new LeggingsView<>(model, true);
        snapshot.setupAnim(frozen);
        var pose = new PoseStack();
        snapshot.root().translateAndRotate(pose);
        var leg = new Recorder();
        snapshot.root().getChild("leg").render(pose, leg, 15728880, 655360);
        require(keys(leg.vertices).equals(keys(vertices(nativeCalls.get(2)))), "Chosen native leg changed geometry/UV/pose");
    }

    private static void submissions(HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        for (boolean inverse : List.of(false, true)) {
            var root = SpikeClient.mixed(inverse, false, false, false);
            var original = root.copy();
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            var leftBefore = data.left().source().copy();
            var rightBefore = data.right().source().copy();
            var output = submit(rig, state, root);
            require(output.nodes.size() == 3 && output.nodes.stream().filter(node -> node.state() instanceof CrystaliteModel.Pose).count() == 2,
                    "Expected two native Crystalite layers and one standard half");
            require(output.nodes.stream().allMatch(node -> node.light() == 15728880 && node.outline() == 0xFF123456),
                    "Buffered light/outline changed");
            require(ItemStack.matches(original, root), "Bridge mutated root provenance");
            require(ItemStack.matches(leftBefore, data.left().source()) && ItemStack.matches(rightBefore, data.right().source()),
                    "Bridge mutated stored source snapshots");
            var fallback = submit(rig, state, SpikeClient.mixed(inverse, true, false, false));
            require(fallback.nodes.size() == 1 && fallback.nodes.stream().noneMatch(node -> node.state() instanceof CrystaliteModel.Pose),
                    "Current complete-owner/neutral baseline changed");
            CrystaliteBridge.forceFailure = true;
            try {
                var failed = submit(rig, state, root);
                require(failed.nodes.size() == 1 && failed.nodes.stream().noneMatch(node -> node.state() instanceof CrystaliteModel.Pose),
                        "Failure after first-side preparation leaked a partial pair");
            } finally {
                CrystaliteBridge.forceFailure = false;
            }
            var unknown = new ItemStack(Items.CHAINMAIL_LEGGINGS);
            require(!EquipmentSources.INSTANCE.usesStandardRenderer(unknown), "Synthetic unknown renderer was not registered");
            var crystal = CrystaliteBridge.nativeStack();
            var pair = MixedLeggingsFactory.createPair(inverse ? unknown : crystal, inverse ? crystal : unknown, CompositionRules.defaults());
            var rejected = submit(rig, state, pair.primary());
            var baseline = pair.primary().copy();
            baseline.set(DataComponents.CUSTOM_NAME, CrystaliteBridge.BASELINE);
            var expected = submit(rig, state, baseline);
            require(!rejected.nodes.isEmpty() && rejected.nodes.size() == expected.nodes.size()
                            && rejected.nodes.stream().noneMatch(node -> node.state() instanceof CrystaliteModel.Pose),
                    "Unknown renderer escaped whole-pair fallback: inverse=" + inverse
                            + ", rejected=" + rejected.nodes.stream().map(node -> node.model().getClass().getName()
                            + "/" + node.state().getClass().getName()).toList()
                            + ", baseline=" + expected.nodes.stream().map(node -> node.model().getClass().getName()).toList());
            for (int index = 0; index < rejected.nodes.size(); index++) {
                require(rejected.nodes.get(index).model() == expected.nodes.get(index).model()
                                && rejected.nodes.get(index).type() == expected.nodes.get(index).type(),
                        "Unknown renderer changed baseline fallback model/material");
            }
        }
        var both = submit(rig, state, SpikeClient.mixed(false, false, true, false));
        require(both.nodes.size() == 4, "Two Crystalite sources did not retain both native layers per half");
        var foil = submit(rig, state, SpikeClient.mixed(false, false, false, true));
        require(foil.nodes.size() == 5, "Live-root foil did not reach both halves");
        var nativeDecorated = CrystaliteBridge.nativeStack();
        nativeDecorated.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        nativeDecorated.set(DataComponents.TRIM, RenderFixture.sources(2)[0].get(DataComponents.TRIM));
        var plain = CrystaliteBridge.nativeCapture(CrystaliteBridge.nativeStack(), state, rig).calls;
        var decorated = CrystaliteBridge.nativeCapture(nativeDecorated, state, rig).calls;
        require(decorated.size() == 6,
                "Native decoration behavior changed; revisit trim/foil assumptions");
        for (int index = 0; index < plain.size(); index++) {
            require(plain.get(index).type() == decorated.get(index).type()
                            && keys(vertices(plain.get(index))).equals(keys(vertices(decorated.get(index)))),
                    "Native trim/foil changed geometry or material");
        }
    }

    private static void visibility(HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        rig.body.visible = false;
        rig.rightLeg.skipDraw = true;
        try {
            var calls = CrystaliteBridge.nativeCapture(CrystaliteBridge.nativeStack(), state, rig).calls;
            require(calls.size() == 4, "Native visibility control changed");
            var reference = new ArrayList<Vertex>();
            for (var call : calls.subList(0, 2)) {
                reference.addAll(vertices(call));
            }
            require(keys(reference).equals(keys(vertices(CrystaliteBridge.model(), CrystaliteModel.freeze(rig)))),
                    "Visible/skipDraw bone semantics differ from native attachments");
        } finally {
            rig.body.visible = true;
            rig.rightLeg.skipDraw = false;
        }
    }

    private static RecordingSubmits submit(HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state, ItemStack root) {
        var output = new RecordingSubmits();
        MixedLeggingsEquipmentRenderer.render(CrystaliteBridge.equipmentRenderer, rig, state,
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0xFF123456);
        return output;
    }

    private static void queueAndCache(HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        rig.setupAnim(state);
        var model = CrystaliteBridge.model();
        var frozen = CrystaliteModel.freeze(rig);
        var first = new LeggingsView<>(model, true);
        var before = keys(vertices(first, frozen));
        var other = new HumanoidRenderState();
        other.isCrouching = true;
        other.isPassenger = true;
        rig.setupAnim(other);
        vertices(new LeggingsView<>(model, false), CrystaliteModel.freeze(rig));
        require(before.equals(keys(vertices(first, frozen))), "Later queued entity changed the first frozen pose");
        LeggingsGeometryCache.clear();
        long builds = LeggingsGeometryCache.statistics().builds();
        for (int index = 0; index < 30; index++) {
            new LeggingsView<>(model, true);
            new LeggingsView<>(model, false);
        }
        require(LeggingsGeometryCache.statistics().builds() == builds + 2, "Native geometry was rebuilt every frame");
    }

    private static List<Vertex> vertices(CrystaliteBridge.NativeCall call) {
        var recorder = new Recorder();
        call.renderer().render(call.pose(), recorder);
        return recorder.vertices;
    }

    private static List<Vertex> vertices(Model<HumanoidRenderState> model, HumanoidRenderState state) {
        var recorder = new Recorder();
        model.setupAnim(state);
        model.renderToBuffer(new PoseStack(), recorder, 15728880, 655360);
        return recorder.vertices;
    }

    private static List<String> keys(List<Vertex> vertices) {
        return vertices.stream().map(Vertex::key).sorted().toList();
    }

    private static double area(List<Vertex> vertices) {
        require(vertices.size() % 4 == 0, "Incomplete native quads");
        double area = 0;
        for (int index = 0; index < vertices.size(); index += 4) {
            var a = vertices.get(index).position();
            var b = vertices.get(index + 1).position().sub(a);
            var c = vertices.get(index + 2).position().sub(a);
            var d = vertices.get(index + 3).position().sub(a);
            area += new Vector3f(b).cross(c).length() / 2.0 + new Vector3f(c).cross(d).length() / 2.0;
        }
        return area;
    }

    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz,
                          int color, int overlay, int light) {
        Vector3f position() {
            return new Vector3f(x, y, z);
        }

        String key() {
            return List.of(Math.round(x * 100000), Math.round(y * 100000), Math.round(z * 100000),
                    Math.round(u * 100000), Math.round(v * 100000), Math.round(nx * 100000),
                    Math.round(ny * 100000), Math.round(nz * 100000), color, overlay, light).toString();
        }
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz, color, overlay, light));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
