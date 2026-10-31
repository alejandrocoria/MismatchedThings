package games.alejandrocoria.mismatchedleggings.test.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.WaistClipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
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

/** Focused behavioral checks against the actual client-baked models, not Java mechanics. */
public final class EquippedChecks {
    private EquippedChecks() {
    }

    public static String run(Minecraft client) {
        var models = client.getEntityModels();
        AvatarRenderState player = new AvatarRenderState();
        player.entityType = EntityType.PLAYER;
        player.isCrouching = true;
        player.walkAnimationPos = 1.4F;
        player.walkAnimationSpeed = 0.7F;
        assertModel(new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), player);
        assertModel(new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM_ARMOR.legs()), true), player);
        player.isCrouching = false;
        player.isPassenger = true;
        assertModel(new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()), false), player);

        HumanoidRenderState baby = new HumanoidRenderState();
        baby.isBaby = true;
        baby.entityType = EntityType.ZOMBIE;
        baby.walkAnimationSpeed = 0.6F;
        baby.walkAnimationPos = 2.0F;
        assertModel(new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_BABY_ARMOR.legs())), baby);

        ArmorStandRenderState stand = new ArmorStandRenderState();
        stand.bodyPose = new Rotations(13, 25, -10);
        stand.leftLegPose = new Rotations(-40, 15, -8);
        stand.rightLegPose = new Rotations(25, -12, 15);
        assertModel(new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs())), stand);
        stand.isSmall = true;
        stand.isBaby = true;
        assertModel(new ArmorStandArmorModel(models.bakeLayer(ModelLayers.ARMOR_STAND_SMALL_ARMOR.legs())), stand);

        assertUvAndInflation();
        WaistHierarchyChecks.run();
        assertNativePasses(new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs())));
        return "geometry.areaAndOwnership=true\ngeometry.uvAndInflation=true\n"
                + "geometry.noSourceMutation=true\nhierarchy.referenceCutAndLiveAnimation=true\n"
                + "models.playerSlimBabyStands=true\n"
                + "animation.crouchPassengerAndPosedStands=true\n"
                + "native.dyeTrimFoilOutline=true\nrejection.atomic=true\n";
    }

    static <S> void assertModel(Model<S> model, S state) {
        LeggingsView<S> left = new LeggingsView<>(model, true);
        LeggingsView<S> right = new LeggingsView<>(model, false);
        String geometryBefore = geometry(model.root());
        List<Vertex> nativeVertices = vertices(model, state);
        List<Vertex> leftVertices = vertices(left, state);
        List<Vertex> rightVertices = vertices(right, state);
        require(!leftVertices.isEmpty() && !rightVertices.isEmpty(), "Empty split model");
        close(area(nativeVertices), area(leftVertices) + area(rightVertices), "Surface lost or duplicated");
        require(geometryBefore.equals(geometry(model.root())), "Source geometry was mutated");
        require(left.root().getChild("leg") == ((HumanoidModel<?>) model).leftLeg
                && right.root().getChild("leg") == ((HumanoidModel<?>) model).rightLeg,
                "Anatomical ownership reversed");
        require(leftVertices.equals(vertices(left, state)), "A → B → A changed the queued view");
    }

    private static void assertUvAndInflation() {
        var mesh = HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), CubeDeformation.NONE).legs();
        ModelPart root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        root.getChild("body").visit(new PoseStack(), (pose, path, index, cube) -> {
            if (!path.isEmpty()) { return; }
            for (boolean left : new boolean[]{true, false}) {
                List<ModelPart.Cube> clipped = WaistClipper.clip(cube, left);
                for (ModelPart.Cube owned : clipped) {
                    for (ModelPart.Polygon face : owned.polygons) {
                        for (ModelPart.Vertex v : face.vertices()) {
                            require(left ? v.x() >= 0 : v.x() <= 0, "Wrong half-space");
                            if (v.x() == 0) {
                                boolean matched = false;
                                for (ModelPart.Polygon original : cube.polygons) {
                                    ModelPart.Vertex[] vertices = original.vertices();
                                    for (int edge = 0; edge < 4; edge++) {
                                        ModelPart.Vertex a = vertices[edge];
                                        ModelPart.Vertex b = vertices[(edge + 1) % 4];
                                        if (a.x() * b.x() >= 0) { continue; }
                                        float t = -a.x() / (b.x() - a.x());
                                        matched |= near(v.y(), a.y() + (b.y() - a.y()) * t)
                                                && near(v.z(), a.z() + (b.z() - a.z()) * t)
                                                && near(v.u(), a.u() + (b.u() - a.u()) * t)
                                                && near(v.v(), a.v() + (b.v() - a.v()) * t);
                                    }
                                }
                                require(matched, "Cut vertex lost native UVs or inflation");
                            }
                        }
                    }
                }
            }
        });
    }

    private static void assertNativePasses(Model<HumanoidRenderState> model) {
        ItemStack[] sources = RenderFixture.sources(2);
        var destination = new RecordingSubmits();
        var root = RenderFixture.pair(2);
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), destination, 15728880, 0xFF123456);
        require(destination.nodes.size() == 6, "Expected native leather base/overlay/trim, gold and root foil on both sources");
        require(destination.nodes.stream().anyMatch(n -> n.color() == 0xFF3366CC), "Native dye lost");
        require(destination.nodes.stream().anyMatch(n -> n.sprite() != null), "Native trim sprite lost");
        require(destination.nodes.stream().allMatch(n -> n.outline() == 0xFF123456), "Outline lost");
        require(destination.nodes.stream().allMatch(n -> n.model() instanceof LeggingsView<?>), "Unsplit node escaped");
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        var noFoil = new RecordingSubmits();
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), noFoil, 15728880, 0);
        require(noFoil.nodes.size() == 4, "Source foil overrode the live root");

        var missing = new ItemStack(Items.GOLDEN_LEGGINGS);
        missing.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build());
        var owner = new RecordingSubmits();
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, RenderFixture.pair(sources[0], missing),
                new PoseStack(), owner, 15728880, 0);
        require(!owner.nodes.isEmpty() && owner.nodes.stream().allMatch(n -> n.model() == model),
                "Unsupported right side did not choose one complete owner");
        var neutral = new RecordingSubmits();
        var missingOwner = RenderFixture.pair(missing, sources[0]);
        require(net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer.shouldRender(missingOwner, EquipmentSlot.LEGS),
                "Native state extraction discarded a valid fallback candidate with no owner asset");
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, missingOwner,
                new PoseStack(), neutral, 15728880, 0);
        require(neutral.nodes.size() == 1 && neutral.nodes.getFirst().model() == model, "Missing owner did not draw neutral");

        var unknown = new BufferedEquipment();
        unknown.order(0).submitCustomGeometry(new PoseStack(), net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(
                net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/iron.png")),
                (pose, consumer) -> {});
        require(!unknown.prepareSplit(true) && !unknown.prepareFull(), "Unknown submission was silently discarded");
        materialFields(model, destination.nodes.stream().filter(node -> node.sprite() != null).findFirst().orElseThrow().sprite());
        cache(model);
    }

    private static void materialFields(Model<HumanoidRenderState> model,
                                       net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        var captured = new BufferedEquipment();
        var state = new HumanoidRenderState();
        var pose = new PoseStack();
        pose.translate(2, 3, 4);
        var expectedPose = pose.last().copy();
        var type = net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(
                net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/iron.png"));
        var crumbling = new net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay(2, pose.last().copy());
        captured.order(7).submitModel(model, state, pose, type, 123, 456, 0xFF135724, sprite, 789, crumbling);
        pose.translate(10, 20, 30);
        require(captured.prepareSplit(true), "Material sentinel preflight failed");
        var forwarded = new RecordingSubmits();
        captured.flush(forwarded);
        var node = forwarded.nodes.getFirst();
        require(node.order() == 7 && node.state() == state && node.type() == type && node.sprite() == sprite
                        && node.light() == 123 && node.overlay() == 456 && node.color() == 0xFF135724
                        && node.outline() == 789 && node.crumbling() == crumbling
                        && node.pose().pose().equals(expectedPose.pose()) && node.pose().normal().equals(expectedPose.normal()),
                "Buffered native material/pose/state fields changed");
    }

    private static void cache(Model<HumanoidRenderState> model) {
        LeggingsGeometryCache.clear();
        long builds = LeggingsGeometryCache.statistics().builds();
        long hits = LeggingsGeometryCache.statistics().hits();
        var a = new LeggingsView<>(model, true);
        new LeggingsView<>(model, false);
        for (int frame = 0; frame < 30; frame++) {
            new LeggingsView<>(model, true).setupAnim(new HumanoidRenderState());
            new LeggingsView<>(model, false).setupAnim(new HumanoidRenderState());
        }
        require(LeggingsGeometryCache.statistics().builds() == builds + 2
                && LeggingsGeometryCache.statistics().hits() >= hits + 60, "Ordinary frames rebuilt geometry");
        var before = vertices(a, new HumanoidRenderState());
        a.setupAnim(new HumanoidRenderState());
        for (int index = 0; index < 160; index++) {
            new LeggingsView<>(new HumanoidModel<HumanoidRenderState>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs())), true);
        }
        require(LeggingsGeometryCache.statistics().geometries() <= 128, "Fresh-model provider caused unbounded retention");
        require(before.equals(vertices(a, new HumanoidRenderState())), "Eviction changed a queued view");
        LeggingsGeometryCache.clear();
        require(LeggingsGeometryCache.statistics().geometries() == 0, "Clear retained old geometry");
        long afterClear = LeggingsGeometryCache.statistics().builds();
        new LeggingsView<>(model, true);
        require(LeggingsGeometryCache.statistics().builds() == afterClear + 1, "Clear reused an obsolete template");
    }

    public static <S> List<Vertex> vertices(Model<? super S> model, S state) {
        Recorder recorder = new Recorder();
        model.setupAnim(state);
        model.renderToBuffer(new PoseStack(), recorder, 15728880, 0);
        return List.copyOf(recorder.vertices);
    }

    static List<Vertex> partVertices(ModelPart part) {
        Recorder recorder = new Recorder();
        part.render(new PoseStack(), recorder, 15728880, 0);
        return List.copyOf(recorder.vertices);
    }

    private static String geometry(ModelPart root) {
        StringBuilder result = new StringBuilder();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            for (ModelPart.Polygon face : cube.polygons) {
                for (ModelPart.Vertex v : face.vertices()) { result.append(v); }
            }
        });
        return result.toString();
    }

    private static double area(List<Vertex> vertices) {
        double result = 0;
        require(vertices.size() % 4 == 0, "Native quad output incomplete");
        for (int i = 0; i < vertices.size(); i += 4) {
            Vector3f a = vertices.get(i).position();
            Vector3f b = vertices.get(i + 1).position().sub(a);
            Vector3f c = vertices.get(i + 2).position().sub(a);
            Vector3f d = vertices.get(i + 3).position().sub(a);
            result += b.cross(c, new Vector3f()).length() / 2.0;
            result += c.cross(d, new Vector3f()).length() / 2.0;
        }
        return result;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.00001F;
    }

    private static void close(double a, double b, String message) {
        require(Math.abs(a - b) < 0.00001, message + ": " + a + " != " + b);
    }

    public static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    public record Vertex(float x, float y, float z, float u, float v) {
        Vector3f position() { return new Vector3f(x, y, z); }
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, u, v));
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
