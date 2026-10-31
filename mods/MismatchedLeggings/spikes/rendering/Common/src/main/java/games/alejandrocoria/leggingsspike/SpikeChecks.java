package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Focused behavioral checks against the actual client-baked models, not Java mechanics. */
public final class SpikeChecks {
    private SpikeChecks() {
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
        ItemStack[] sources = Fixture.sources(2);
        BufferedEquipment destination = new BufferedEquipment(true);
        require(PairRenderer.render(SpikeClient.nativeRenderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, sources[0], sources[1], new PoseStack(),
                destination, 15728880, 0xFF123456), "Native decorated pair rejected");
        require(destination.size() == 5, "Expected leather base/overlay/foil/trim plus gold");
        require(destination.snapshot().stream().anyMatch(n -> n.color() == 0xFF3366CC), "Native dye lost");
        require(destination.snapshot().stream().anyMatch(n -> n.sprite() != null), "Native trim sprite lost");
        require(destination.snapshot().stream().allMatch(n -> n.outline() == 0xFF123456), "Outline lost");
        require(destination.snapshot().stream().allMatch(n -> n.model() instanceof LeggingsView<?>), "Unsplit node escaped");

        BufferedEquipment rejected = new BufferedEquipment(true);
        ItemStack missing = Fixture.source(Items.GOLDEN_LEGGINGS, EquipmentAssets.GOLD);
        missing.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build());
        require(!PairRenderer.render(SpikeClient.nativeRenderer, model, new HumanoidRenderState(),
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, sources[0], missing, new PoseStack(),
                rejected, 15728880, 0), "Missing asset accepted");
        require(rejected.size() == 0, "Rejected pair partially submitted");
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
