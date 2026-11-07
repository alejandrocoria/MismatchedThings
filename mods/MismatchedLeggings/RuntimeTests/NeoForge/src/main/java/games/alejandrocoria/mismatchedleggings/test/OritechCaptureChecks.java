package games.alejandrocoria.mismatchedleggings.test;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.event.armor.CompileArmorRenderStateEvent;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechRenderState;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentCapture;
import games.alejandrocoria.mismatchedleggings.compatibility.oritech.OritechMixinPlugin;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Production-helper comparisons, no opt-in spike sources, model copies or render interception. */
final class OritechCaptureChecks {
    private static int ticks;
    private static int phase;
    private static CompletableFuture<Void> reload;
    private static LivingEntity observedWearer;
    private static float observedPartialTick;
    private static GeoRenderState observedNativeState;
    private static EquipmentCapture stale;
    private static OritechPairChecks.Scene scene;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private OritechCaptureChecks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((CompileArmorRenderStateEvent<?, ?> event) -> {
            if (event.getRenderData().baseModel().getClass().getSimpleName().equals("ExoPose")) {
                observedWearer = event.getRenderData().entity();
                observedPartialTick = ((GeoRenderState) event.getRenderState()).getPartialTick();
                observedNativeState = (GeoRenderState) event.getRenderState();
                require(observedWearer.getItemBySlot(EquipmentSlot.LEGS).getItem()
                                != event.getRenderData().itemStack().getItem(),
                        "Production source extraction temporarily replaced the equipped mixed root");
            }
        });
    }

    static void tick(Minecraft client) {
        if (phase == 3) {
            return;
        }
        try {
            require(++ticks < 1800, "Oritech production capture timed out");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated window became visible");
            if (captureFailure != null) {
                throw new AssertionError("Production Exo panel capture failed", captureFailure);
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) {
                return;
            }
            if (phase == 0 && client.screen == null) {
                verify(client);
                reload = client.reloadResourcePacks();
                phase = 1;
            } else if (phase == 1 && reload.isDone()) {
                reload.join();
                var rejected = new Geometry();
                boolean staleRefused = false;
                try {
                    stale.flush(rejected);
                } catch (IllegalStateException expected) {
                    staleRefused = true;
                }
                require(staleRefused && rejected.nodes.isEmpty(), "Pre-reload deferred data escaped");
                scene = new OritechPairChecks.Scene(client);
                client.setScreen(scene);
                phase = 2;
            } else if (phase == 2 && scene.frames >= 8) {
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(Path.of(System.getProperty("leggings.test.report"), "oritech-pairs.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                phase = 4;
            } else if (phase == 4 && captured) {
                verify(client);
                Files.writeString(Path.of(System.getProperty("leggings.test.report"), "oritech-capture.txt"),
                        "production.independentNativeExtraction=true\ncontext.actualWearerAndFractionalTick=true\n"
                                + "pose.ownedAndQueuedABA=true\nnative.verticesUVNormalsMaterialsLighting=true\n"
                                + "capture.fullBothMasksAndNoHistoryMutation=true\nrejection.staleMissingBonesExtraSubmissions=true\n"
                                + "guard.localApiChangesAndUnrelatedEdits=true\nreload.recoverAndInvalidate=true\n"
                                + "dispatch.bothOrientationsTwoExoAndOrdinary=true\nfallback.fullExoOwnerOrNeutralAtomic=true\n"
                                + "fallback.failedEpochSafeOrdinaryOwner=true\n");
                phase = 3;
                SkeletonChecks.passed("client");
                client.stop();
            }
        } catch (Throwable failure) {
            phase = 3;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    static ItemStack exo() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("oritech:exo_leggings")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify(Minecraft client) throws Exception {
        require(EquippedClient.renderer != null, "Equipment renderer not observed");
        apiGuards();
        var source = exo();
        var root = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults()).primary();
        var before = root.copy();
        var rig = new ArmorStandArmorModel(client.getEntityModels().bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs()));
        Geometry first = null;
        List<Vertex> firstVertices = null;
        EquipmentCapture last = null;
        ArmorStandRenderState lastState = null;
        for (boolean posed : List.of(false, true)) {
            var actor = actor(client, root, posed);
            observedWearer = null;
            var state = state(client, actor);
            require(observedWearer == actor && observedPartialTick == 0.375F, "Wrong wearer or fractional tick in native extraction");
            require(ItemStack.matches(before, actor.getItemBySlot(EquipmentSlot.LEGS)), "Native extraction mutated live equipment");
            require(((OritechRenderState) state).mismatchedleggings$exoFrame() != null, "Independent source context missing");

            var nativeActor = actor(client, source, posed);
            var nativeState = state(client, nativeActor);
            var control = new Geometry();
            java.util.function.BiFunction model = (s, slot) -> rig;
            require(GeoArmorRenderer.tryRenderGeoArmorPiece(model, new PoseStack(), control, source,
                    EquipmentSlot.LEGS, 15728880, (ArmorStandRenderState & GeoRenderState) nativeState), "Native armor-layer control missing");
            var complete = OritechEquipmentCapture.capture(source, state, new PoseStack(), 0);
            require(complete != null && complete.prepareFull(), "Production complete source preflight failed");
            var full = flush(complete);
            require(vertices(control).equals(vertices(full)), "Production source differs from native geometry/UV/normal/light");
            require(control.nodes.getFirst().type == full.nodes.getFirst().type, "Native material changed");
            var left = OritechEquipmentCapture.capture(source, state, new PoseStack(), 0);
            var right = OritechEquipmentCapture.capture(source, state, new PoseStack(), 0);
            require(left.prepareSplit(true) && right.prepareSplit(false), "Native side masks failed");
            var a = flush(left);
            stale = left;
            var av = vertices(a);
            var bv = vertices(flush(right));
            require(av.size() == 24 && bv.size() == 24, "Native mask included extra leg/waist/boots");
            var union = new ArrayList<>(av);
            union.addAll(bv);
            require(sorted(union).equals(sorted(vertices(control))), "Masked union differs from native source");
            require(av.equals(vertices(a)), "A/B/A changed queued source state");
            if (first != null) {
                require(firstVertices.equals(vertices(first)), "Another wearer/pose changed earlier native tasks");
                require(!firstVertices.equals(av), "Independent native pose was not applied");
            }
            first = a;
            firstVertices = av;
            // Also mutate the borrowed native rig, after native task preparation.
            rig.leftLeg.xRot = 2.5F;
            rig.rightLeg.yRot = -1.5F;
            require(av.equals(vertices(a)), "Native tasks retained a mutable borrowed rig");
            last = complete;
            lastState = state;
        }
        require(ItemStack.matches(root, before), "Capture changed provenance");

        OritechPairChecks.verify(client);
        submissionGuards(controlMaterial(client, source), rig);
        var actor = actor(client, root, false);
        var target = state(client, actor);
        var rejectedTarget = target;
        OritechEquipmentCapture.extract(actor, target, 0.375F, () -> rejectedTarget, ignored -> rig);
        require(((OritechRenderState) target).mismatchedleggings$exoFrame() == null && !OritechEquipmentCapture.extracting(),
                "Reused-state rejection left stale context/reentrancy guard");
        OritechEquipmentCapture.extract(actor, target, 0.375F, HumanoidRenderState::new, ignored -> rig);
        require(((OritechRenderState) target).mismatchedleggings$exoFrame() == null && !OritechEquipmentCapture.extracting(),
                "Unsupported pose poisoned extraction context");
        target = state(client, actor);
        require(((OritechRenderState) target).mismatchedleggings$exoFrame() != null, "Unsupported request disabled valid poses");
        target.legsEquipment = new ItemStack(Items.IRON_LEGGINGS);
        require(OritechEquipmentCapture.capture(source, target, new PoseStack(), 0) == null, "Changed root reused old context");

        // Controlled resource incompatibility, always restored; disablement must recover on real reload.
        var renderer = GeoRenderProvider.of(source).getGeoArmorRenderer(source, EquipmentSlot.LEGS);
        var modelResource = renderer.getGeoModel().getModelResource(observedNativeState);
        var lookup = renderer.getGeoModel().getBakedModel(modelResource).boneLookup().get();
        var removed = lookup.remove("armorRightLeg");
        require(removed != null, "Native missing-bone control was not installed");
        try {
            require(!last.prepareSplit(true), "Missing native leg was accepted");
            require(OritechEquipmentCapture.capture(source, lastState, new PoseStack(), 0) == null, "Failed epoch kept retrying capture");
            OritechPairChecks.failedEpoch(client);
        } finally {
            lookup.put("armorRightLeg", removed);
        }
    }

    static ArmorStand actor(Minecraft client, ItemStack stack, boolean posed) {
        var actor = new ArmorStand(client.level, 0, 80, 0);
        actor.setItemSlot(EquipmentSlot.LEGS, stack.copy());
        if (posed) {
            actor.setLeftLegPose(new Rotations(-35, 10, -10));
            actor.setRightLegPose(new Rotations(25, -10, 12));
        }
        return actor;
    }

    static ArmorStandRenderState state(Minecraft client, ArmorStand actor) {
        return (ArmorStandRenderState) client.getEntityRenderDispatcher().getRenderer(actor).createRenderState(actor, 0.375F);
    }

    static Geometry flush(EquipmentCapture capture) {
        var output = new Geometry();
        capture.flush(output);
        return output;
    }

    private static RenderType controlMaterial(Minecraft client, ItemStack source) {
        var renderer = GeoRenderProvider.of(source).getGeoArmorRenderer(source, EquipmentSlot.LEGS);
        return renderer.getRenderType(null, Identifier.parse("oritech:textures/entity/armor/exo_armor.png"));
    }

    private static void submissionGuards(RenderType material, ArmorStandArmorModel rig) throws Exception {
        var type = Class.forName("games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.ExoGeometryBuffer");
        var constructor = type.getDeclaredConstructor(RenderType.class);
        constructor.setAccessible(true);
        var valid = type.getDeclaredMethod("valid");
        valid.setAccessible(true);
        var buffer = (SubmitNodeStorage) constructor.newInstance(material);
        buffer.order(0).submitCustomGeometry(new PoseStack(), material, (pose, vertices) -> { });
        require((boolean) valid.invoke(buffer), "Valid native geometry was rejected");
        boolean rejected = false;
        try {
            buffer.order(0).submitCustomGeometry(new PoseStack(), material, (pose, vertices) -> { });
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected && !(boolean) valid.invoke(buffer), "Duplicate native pass was accepted");
        buffer = (SubmitNodeStorage) constructor.newInstance(material);
        buffer.order(0).submitCustomGeometry(new PoseStack(), material, (pose, vertices) -> { });
        buffer.order(0).submitModel(rig, new ArmorStandRenderState(), new PoseStack(), material, 0, 0, -1,
                null, 0, null);
        require(!(boolean) valid.invoke(buffer), "Unknown model submission was silently dropped");
        var flush = type.getDeclaredMethod("flush", SubmitNodeCollector.class);
        flush.setAccessible(true);
        var destination = new Geometry();
        boolean refused = false;
        try {
            flush.invoke(buffer, destination);
        } catch (java.lang.reflect.InvocationTargetException expected) {
            refused = expected.getCause() instanceof IllegalStateException;
        }
        require(refused && destination.nodes.isEmpty(), "Rejected submission reached the destination");
    }

    private static void apiGuards() throws Exception {
        var types = new HashMap<String, ClassNode>();
        for (String name : List.of("com.geckolib.renderer.GeoArmorRenderer", "com.geckolib.animatable.client.GeoRenderProvider",
                "com.geckolib.animation.state.BoneSnapshot")) {
            try (var stream = OritechCaptureChecks.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                var node = new ClassNode();
                new ClassReader(stream).accept(node, 0);
                types.put(name, node);
            }
        }
        require(OritechMixinPlugin.hasApi(types) && !OritechMixinPlugin.hasApi(java.util.Map.of()), "Native/absent API shape guard failed");
        var armor = types.get("com.geckolib.renderer.GeoArmorRenderer");
        armor.methods.getFirst().instructions.clear();
        require(OritechMixinPlugin.hasApi(types), "Unrelated method body disabled the API guard");
        armor.methods.removeIf(method -> method.name.equals("getSegmentsForSlot"));
        require(!OritechMixinPlugin.hasApi(types), "Missing required native operation was accepted");
    }

    static List<Vertex> vertices(Geometry geometry) {
        var output = new Recorder();
        geometry.nodes.forEach(node -> node.renderer.render(node.pose, output));
        return List.copyOf(output.vertices);
    }

    private static List<Vertex> sorted(List<Vertex> vertices) {
        return vertices.stream().sorted(Comparator.comparing(Vertex::toString)).toList();
    }

    static final class Geometry extends SubmitNodeStorage {
        final List<Node> nodes = new ArrayList<>();

        @Override
        public SubmitNodeCollection order(int order) {
            return new SubmitNodeCollection(this) {
                @Override
                public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
                    nodes.add(new Node(pose.last().copy(), type, renderer));
                }
            };
        }
    }

    record Node(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) { }
    record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int overlay, int color) { }

    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz, light, overlay, color));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
