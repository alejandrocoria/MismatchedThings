package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.client.compatibility.betterend.CrystaliteEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native callback oracle versus the production capture, without the prototype renderer mixin. */
final class CrystaliteCaptureChecks {
    private static final int LIGHT = 15728880;
    private static final int OVERLAY = 655360;
    private static final int OUTLINE = 0xFF123456;
    private static Model<?> previousEpochModel;

    private CrystaliteCaptureChecks() {
    }

    @SuppressWarnings("unchecked")
    static String run() {
        var models = Minecraft.getInstance().getEntityModels();
        var rig = new HumanoidModel<HumanoidRenderState>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        var state = new HumanoidRenderState();
        var source = BetterEndChecks.nativeStack("crystalite_leggings");
        var original = source.copy();
        var first = full(source, rig, state, false).nodes.getFirst();
        require(previousEpochModel == null || previousEpochModel != first.model(),
                "Crystalite geometry survived a resource reload");

        compare(source, rig, state);
        var player = new AvatarRenderState();
        player.entityType = EntityType.PLAYER;
        player.isCrouching = true;
        player.walkAnimationPos = 1.4F;
        player.walkAnimationSpeed = 0.7F;
        compare(source, (HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>)
                new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), player);
        compare(source, (HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>)
                new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM_ARMOR.legs()), true), player);
        player.isCrouching = false;
        player.isPassenger = true;
        compare(source, (HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>)
                new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), player);

        var baby = new HumanoidRenderState();
        baby.isBaby = true;
        baby.entityType = EntityType.ZOMBIE;
        baby.walkAnimationPos = 2;
        baby.walkAnimationSpeed = 0.6F;
        compare(source, new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_BABY_ARMOR.legs())), baby);
        var stand = new ArmorStandRenderState();
        stand.bodyPose = new Rotations(13, 25, -10);
        stand.leftLegPose = new Rotations(-40, 15, -8);
        stand.rightLegPose = new Rotations(25, -12, 15);
        compare(source, (HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>)
                new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs())), stand);
        stand.isSmall = true;
        stand.isBaby = true;
        compare(source, (HumanoidModel<HumanoidRenderState>) (HumanoidModel<?>)
                new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_SMALL_ARMOR.legs())), stand);

        rig.body.visible = false;
        rig.rightLeg.skipDraw = true;
        try {
            compare(source, rig, state);
        } finally {
            rig.body.visible = true;
            rig.rightLeg.skipDraw = false;
        }
        queueAndCache(source, rig, state);
        var foil = full(source, rig, state, true);
        require(foil.nodes.size() == 3 && foil.nodes.getLast().type() == RenderTypes.armorEntityGlint(),
                "Crystalite root foil did not add exactly one armor-glint pass");
        require(foil.nodes.stream().allMatch(node -> node.light() == LIGHT && node.overlay() == OVERLAY
                        && node.outline() == OUTLINE && node.color() == -1),
                "Crystalite submissions changed light/overlay/outline/color");
        require(CrystaliteEquipmentCapture.capture(new ItemStack(Items.IRON_LEGGINGS), rig, state, false,
                        new PoseStack(), LIGHT, OUTLINE) == null,
                "Crystalite bridge claimed an unrelated source");
        require(CrystaliteEquipmentCapture.capture(source, rig, new Object(), false,
                        new PoseStack(), LIGHT, OUTLINE) == null,
                "Crystalite bridge accepted an unsupported render state");
        require(full(source, rig, state, false).nodes.size() == 2,
                "Unsupported request disabled normal Crystalite capture");
        require(ItemStack.matches(original, source), "Crystalite capture mutated source components");
        dispatch(source, rig, state);
        previousEpochModel = full(source, rig, state, false).nodes.getFirst().model();
        return "crystalite.nativeGeometryUvNormalsMaterials=true\ncrystalite.complementarySplitAndNativeLeg=true\n"
                + "crystalite.playerSlimCrouchSeatedBabyStands=true\ncrystalite.visibilityAndSkipDraw=true\n"
                + "crystalite.queueAndBoundedCache=true\ncrystalite.rootFoilAndRenderFields=true\n"
                + "crystalite.unsupportedRequestAndNonMutation=true\n"
                + "crystalite.productionDispatchBothOrientationsAndFoil=true\n"
                + "crystalite.unknownOwnerNeutralAndAtomicFallback=true\n"
                + "crystalite.incompatibleProbeEpochDisableAndRecovery=true\n";
    }

    private static void dispatch(ItemStack source, HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        require(EquippedClient.renderer != null, "Missing live equipment renderer for production dispatch");
        require(!EquipmentSources.INSTANCE.usesStandardRenderer(source), "Crystalite was falsely marked standard");
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        var unknown = new ItemStack(Items.CHAINMAIL_LEGGINGS);
        require(!EquipmentSources.INSTANCE.usesStandardRenderer(unknown), "Synthetic unknown renderer was not registered");
        var original = source.copy();
        for (boolean inverse : List.of(false, true)) {
            var root = pair(inverse ? iron : source, inverse ? source : iron, false);
            var before = root.copy();
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            var left = data.left().source().copy();
            var right = data.right().source().copy();
            var output = submit(root, rig, state);
            require(output.nodes.size() == 3 && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>)
                            && output.nodes.stream().filter(node -> node.state() != state).count() == 2,
                    "Production did not split two Crystalite layers and one standard layer: inverse=" + inverse);
            require(output.nodes.stream().allMatch(node -> node.light() == LIGHT && node.outline() == OUTLINE),
                    "Production dispatch changed captured light/outline");
            require(ItemStack.matches(before, root) && ItemStack.matches(left, data.left().source())
                            && ItemStack.matches(right, data.right().source()),
                    "Production dispatch mutated root or source snapshots");
            root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            var foil = submit(root, rig, state);
            require(foil.nodes.size() == 5
                            && foil.nodes.stream().filter(node -> node.type() == RenderTypes.armorEntityGlint()).count() == 2,
                    "Root foil did not reach both production halves");
        }
        require(submit(pair(source, source, false), rig, state).nodes.size() == 4,
                "Double Crystalite did not preserve two native layers per half");
        var standard = submit(pair(iron, new ItemStack(Items.GOLDEN_LEGGINGS), false), rig, state);
        require(standard.nodes.size() == 2 && standard.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                "Known custom dispatch changed standard equipment splitting");
        var noAsset = source.copy();
        noAsset.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build());
        require(submit(pair(noAsset, iron, false), rig, state).nodes.size() == 3,
                "Standard asset guard incorrectly rejected known custom geometry");

        var owner = submit(pair(source, unknown, false), rig, state);
        sameSubmissions(owner, full(source, rig, state, false), "Unknown right did not retain safe complete Crystalite owner");
        var neutral = submit(pair(unknown, unknown, false), rig, state);
        require(neutral.nodes.size() == 1 && !(neutral.nodes.getFirst().model() instanceof LeggingsView<?>),
                "Unknown owners did not choose a complete neutral model");
        sameSubmissions(submit(pair(unknown, source, false), rig, state), neutral,
                "Unknown left did not choose neutral fallback");

        CrystaliteFailureControls.preparedLeft = 0;
        CrystaliteFailureControls.rejectedRight = 0;
        CrystaliteFailureControls.failRightSplit = true;
        try {
            sameSubmissions(submit(pair(source, iron, false), rig, state), full(source, rig, state, false),
                    "Second-half failure leaked partial split output instead of the complete owner");
            require(CrystaliteFailureControls.preparedLeft == 1 && CrystaliteFailureControls.rejectedRight == 1,
                    "Atomic-fallback control did not fail after preparing the first half");
        } finally {
            CrystaliteFailureControls.failRightSplit = false;
        }

        var standardOwner = submit(pair(iron, unknown, false), rig, state);
        LeggingsGeometryCache.clear();
        CrystaliteFailureControls.probeAttempts = 0;
        CrystaliteFailureControls.dropProbeCallback = true;
        try {
            sameSubmissions(submit(pair(source, iron, false), rig, state), neutral,
                    "Incompatible material probe did not fall back to neutral");
            sameSubmissions(submit(pair(iron, source, false), rig, state), standardOwner,
                    "Incompatible material probe replaced a safe standard owner");
            require(CrystaliteFailureControls.probeAttempts == 1, "Failed adapter retried its material probe in the same epoch");
        } finally {
            CrystaliteFailureControls.dropProbeCallback = false;
        }
        LeggingsGeometryCache.clear();
        require(submit(pair(source, iron, false), rig, state).nodes.size() == 3,
                "A new resource epoch did not recover Crystalite rendering after an incompatible probe");
        var bare = new ItemStack(pair(iron, iron, false).getItem());
        require(submit(bare, rig, state).nodes.isEmpty(), "Bare invalid root entered production capture");
        require(ItemStack.matches(original, source), "Production checks mutated the native input");
    }

    private static ItemStack pair(ItemStack left, ItemStack right, boolean foil) {
        var root = RenderFixture.pair(left, right);
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return root;
    }

    private static RecordingSubmits submit(ItemStack root, HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        var output = new RecordingSubmits();
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state,
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, LIGHT, OUTLINE);
        return output;
    }

    private static void sameSubmissions(RecordingSubmits actual, RecordingSubmits expected, String message) {
        require(!expected.nodes.isEmpty() && actual.nodes.size() == expected.nodes.size(), message);
        for (int index = 0; index < expected.nodes.size(); index++) {
            var a = actual.nodes.get(index);
            var b = expected.nodes.get(index);
            require(a.model() == b.model() && a.type() == b.type() && keys(vertices(a)).equals(keys(vertices(b))), message);
        }
    }

    private static void compare(ItemStack source, HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        rig.setupAnim(state);
        var nativeCalls = new NativeCalls();
        var renderer = ArmorRendererRegistryImpl.get(source.getItem());
        require(renderer != null, "Native Crystalite renderer missing");
        renderer.render(new PoseStack(), nativeCalls, source, state, EquipmentSlot.LEGS, LIGHT, rig);
        int parts = rig.body.visible ? 3 : 2;
        require(nativeCalls.calls.size() == parts * 2, "Native Crystalite layer/visibility contract changed");
        var expected = new ArrayList<Vertex>();
        for (var call : nativeCalls.calls.subList(0, parts)) {
            expected.addAll(vertices(call));
        }
        var full = full(source, rig, state, false);
        require(full.nodes.size() == 2 && full.nodes.getFirst().type() == nativeCalls.calls.getFirst().type()
                        && full.nodes.getLast().type() == nativeCalls.calls.get(parts).type(),
                "Capture did not reuse both native materials");
        var actual = vertices(full.nodes.getFirst());
        require(!expected.isEmpty() && keys(expected).equals(keys(actual)),
                "Captured Crystalite geometry differs from native callbacks: " + rig.getClass());
        require(keys(expected).equals(keys(vertices(full.nodes.getLast()))), "Second native layer lost geometry");

        var left = split(source, rig, state, true).nodes.getFirst();
        var right = split(source, rig, state, false).nodes.getFirst();
        var leftVertices = vertices(left);
        var rightVertices = vertices(right);
        require(!leftVertices.isEmpty() && !rightVertices.isEmpty()
                        && Math.abs(area(actual) - area(leftVertices) - area(rightVertices)) < 0.00001,
                "Crystalite split lost or duplicated native surface");
        require(left.model() instanceof LeggingsView<?>, "Capture bypassed the existing waist pipeline");
        var leg = new Recorder();
        animate(left);
        var pose = new PoseStack();
        left.model().root().translateAndRotate(pose);
        left.model().root().getChild("leg").render(pose, leg, LIGHT, OVERLAY);
        require(!leg.vertices.isEmpty() && keys(leg.vertices).equals(keys(vertices(nativeCalls.calls.get(parts - 1)))),
                "Selected Crystalite leg changed native geometry or pose");
    }

    private static void queueAndCache(ItemStack source, HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        var first = split(source, rig, state, true).nodes.getFirst();
        var before = keys(vertices(first));
        var other = new HumanoidRenderState();
        other.isCrouching = true;
        other.isPassenger = true;
        var second = split(source, rig, other, false).nodes.getFirst();
        require(!before.equals(keys(vertices(second))) && before.equals(keys(vertices(first))),
                "Queued Crystalite poses are not independent");

        LeggingsGeometryCache.clear();
        long builds = LeggingsGeometryCache.statistics().builds();
        var stable = full(source, rig, state, false).nodes.getFirst().model();
        for (int index = 0; index < 30; index++) {
            split(source, rig, state, true);
            split(source, rig, state, false);
        }
        require(LeggingsGeometryCache.statistics().builds() == builds + 2
                        && full(source, rig, state, false).nodes.getFirst().model() == stable,
                "Crystalite capture rebuilt native geometry/templates each frame");
    }

    private static RecordingSubmits full(ItemStack source, HumanoidModel<?> rig, HumanoidRenderState state, boolean foil) {
        return capture(source, rig, state, foil, null);
    }

    private static RecordingSubmits split(ItemStack source, HumanoidModel<?> rig, HumanoidRenderState state, boolean left) {
        return capture(source, rig, state, false, left);
    }

    private static RecordingSubmits capture(ItemStack source, HumanoidModel<?> rig, HumanoidRenderState state,
                                             boolean foil, Boolean left) {
        var buffered = CrystaliteEquipmentCapture.capture(source, rig, state, foil, new PoseStack(), LIGHT, OUTLINE);
        require(buffered != null, "Production Crystalite adapter refused a supported capture");
        require(left == null ? buffered.prepareFull() : buffered.prepareSplit(left), "Crystalite buffer preflight failed");
        var result = new RecordingSubmits();
        buffered.flush(result);
        return result;
    }

    private static <S> void animate(RecordingSubmits.Node<S> node) {
        node.model().setupAnim(node.state());
    }

    private static List<Vertex> vertices(RecordingSubmits.Node<?> node) {
        animate(node);
        var pose = new PoseStack();
        pose.last().set(node.pose());
        var recorder = new Recorder();
        node.model().renderToBuffer(pose, recorder, node.light(), node.overlay(), node.color());
        return recorder.vertices;
    }

    private static List<Vertex> vertices(NativeCall call) {
        var recorder = new Recorder();
        call.renderer().render(call.pose(), recorder);
        return recorder.vertices;
    }

    private static List<String> keys(List<Vertex> vertices) {
        return vertices.stream().map(Vertex::key).sorted().toList();
    }

    private static double area(List<Vertex> vertices) {
        require(vertices.size() % 4 == 0, "Incomplete Crystalite quads");
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

    private static final class NativeCalls extends SubmitNodeStorage {
        private final List<NativeCall> calls = new ArrayList<>();

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            calls.add(new NativeCall(pose.last().copy(), type, renderer));
        }
    }

    private record NativeCall(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

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

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
