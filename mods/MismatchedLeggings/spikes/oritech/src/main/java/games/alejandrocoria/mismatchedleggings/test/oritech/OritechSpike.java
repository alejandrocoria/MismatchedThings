package games.alejandrocoria.mismatchedleggings.test.oritech;

import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class OritechSpike {
    public static boolean enabled;
    private static int phase;
    private static int ticks;
    private static Scene scene;
    private static CompletableFuture<Void> reload;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static String evidence;

    private OritechSpike() {
    }

    public static void tick(Minecraft client) {
        if (phase == 6) {
            return;
        }
        try {
            require(++ticks < 2400, "Oritech spike timed out: " + phase + " / " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated window became visible");
            if (captureFailure != null) {
                throw new AssertionError("Oritech screenshot failed", captureFailure);
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) {
                return;
            }
            if (phase == 0 && client.screen == null) {
                SkeletonChecks.verify();
                scene = new Scene(client);
                client.setScreen(scene);
                phase = 1;
            } else if (phase == 1 && scene.frames >= 8) {
                require(EquippedClient.renderer != null, "Native equipment renderer not observed");
                baseline(client);
                screenshot(client, "oritech-before.png");
                phase = 2;
            } else if (phase == 2 && captured) {
                enabled = true;
                evidence = verify(client);
                scene = new Scene(client);
                client.setScreen(scene);
                phase = 3;
            } else if (phase == 3 && scene.frames >= 8 && captured) {
                screenshot(client, "oritech-after.png");
                phase = 4;
            } else if (phase == 4 && captured) {
                reload = client.reloadResourcePacks();
                phase = 5;
            } else if (phase == 5 && reload.isDone()) {
                reload.join();
                evidence += "reload.sameAssertions=true\n" + verify(client);
                // The gallery remains active; allow the reloaded frame to be rendered before capture.
                scene = new Scene(client);
                client.setScreen(scene);
                phase = 7;
            } else if (phase == 7 && scene.frames >= 8) {
                screenshot(client, "oritech-reload.png");
                phase = 8;
            } else if (phase == 8 && captured) {
                Files.writeString(report("oritech-spike.txt"), evidence);
                SkeletonChecks.passed("client");
                phase = 6;
                client.stop();
            }
        } catch (Throwable failure) {
            SkeletonChecks.failed("client", failure);
            phase = 6;
            client.stop();
        }
    }

    private static ItemStack exo() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("oritech:exo_leggings")));
    }

    private static void baseline(Minecraft client) {
        var pair = MixedLeggingsFactory.createPair(exo(), new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        var rig = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var output = new RecordingSubmits();
            MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, new HumanoidRenderState(),
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0);
            require(output.nodes.size() == 1, "Expected one complete fallback before adaptation");
            require(output.nodes.getFirst().model() == rig, "Expected complete owner/neutral, not a split capture");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String verify(Minecraft client) {
        var source = exo();
        var original = source.copy();
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var equipment = root.get(DataComponents.EQUIPPABLE);
            require(equipment.equipSound().value().location().toString().equals("oritech:short_servo"), "Servo sound lost");
            require(!root.has(DataComponents.UNBREAKABLE), "One Exo source made root unbreakable");
            for (String attribute : List.of("movement_speed", "flying_speed")) {
                var entry = root.getAttributeModifiers().modifiers().stream()
                        .filter(value -> value.attribute().unwrapKey().orElseThrow().identifier().getPath().equals(attribute))
                        .findFirst().orElseThrow();
                require(Math.abs(entry.modifier().amount() - 0.1) < 0.000001, "Speed did not average");
            }
            require(root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).stream()
                    .anyMatch(line -> line.getString().equals("Increases movement speed")), "Native source tooltip lost");
            for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                require(faces.stream().anyMatch(face -> face.texture().contains("exo_leggings"))
                                && faces.stream().anyMatch(face -> face.texture().contains("iron_leggings")),
                        "Generated item lost a source in " + context);
            }
        }
        var both = MixedLeggingsFactory.createPair(source, source, CompositionRules.defaults()).primary();
        require(both.has(DataComponents.UNBREAKABLE), "Two native unbreakable sources lost marker");
        var rig = new ArmorStandArmorModel(client.getEntityModels().bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs()));
        GeometryCapture queued = null;
        List<Vertex> queuedVertices = null;
        for (boolean posed : List.of(false, true)) {
            var stand = new ArmorStand(client.level, 0, 80, 0);
            stand.setItemSlot(EquipmentSlot.LEGS, source.copy());
            if (posed) {
                stand.setLeftLegPose(new Rotations(-35, 10, -10));
                stand.setRightLegPose(new Rotations(25, -10, 12));
            }
            var state = (ArmorStandRenderState) client.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand, 0);
            var control = new GeometryCapture();
            java.util.function.BiFunction nativeModel = (s, slot) -> rig;
            require(GeoArmorRenderer.tryRenderGeoArmorPiece(nativeModel, new PoseStack(), control,
                    source, EquipmentSlot.LEGS, 15728880, (ArmorStandRenderState & GeoRenderState) state), "Native Gecko armor-layer control was not handled");
            var nativeVertices = vertices(control);
            require(nativeVertices.size() == 48, "Native leggings should have two cuboids, not waist/boots/chest: " + nativeVertices.size());
            var full = new GeometryCapture();
            NativeExoBridge.nativePass(source, rig, state, new PoseStack(), full, 15728880, 0, null);
            require(sorted(nativeVertices).equals(sorted(vertices(full))), "Source-specific capture differs from real native armor path: "
                    + nativeVertices.getFirst() + " / " + vertices(full).getFirst());
            var left = new GeometryCapture();
            NativeExoBridge.nativePass(source, rig, state, new PoseStack(), left, 15728880, 0, true);
            var leftBefore = vertices(left);
            var right = new GeometryCapture();
            NativeExoBridge.nativePass(source, rig, state, new PoseStack(), right, 15728880, 0, false);
            var rightVertices = vertices(right);
            require(leftBefore.size() == 24 && rightVertices.size() == 24, "Side masks did not isolate a native leg");
            var union = new ArrayList<>(leftBefore);
            union.addAll(rightVertices);
            require(sorted(nativeVertices).equals(sorted(union)), "Split geometry lost/duplicated native vertices, UVs or normals");
            require(leftBefore.equals(vertices(left)), "Deferred A/B/A changed a queued source pose/mask");
            if (queued != null) {
                require(queuedVertices.equals(vertices(queued)), "Later pose capture changed a previously queued frame");
                require(!queuedVertices.equals(leftBefore), "Posed native leg did not move");
            }
            queued = left;
            queuedVertices = leftBefore;
            for (var root : List.of(pair.primary(), pair.inverse(), both)) {
                var before = root.copy();
                var output = new GeometryCapture();
                require(NativeExoBridge.mixed(EquippedClient.renderer, rig, state, root, new PoseStack(), output, 15728880, 0),
                        "Mixed pair preflight failed");
                require(vertices(output).size() == (root == both ? 48 : 24), "Mixed pass lost or duplicated Exo geometry");
                require(ItemStack.matches(before, root), "Render capture modified root/source history");
            }
        }
        String decoration = decorations(client, rig);
        require(ItemStack.matches(source, original), "Native source mutated");
        return "baseline.completeFallbackReproduced=true\nnative.controlAndSourceCaptureExact=true\n"
                + "native.legsOnlyNoWaist=true\nmasked.verticesUVNormalsLightColorExactUnion=true\n"
                + "queued.ABAIsolation=true\nposed.twoIndependentLegs=true\nroot.historyUnchanged=true\n"
                + "composition.speedSoundUnbreakableTooltip=true\nitem.guiAndHandsBothSources=true\n" + decoration;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String decorations(Minecraft client, HumanoidModel<?> rig) {
        int passes = -1;
        for (boolean decorated : List.of(false, true)) {
            var source = exo();
            if (decorated) {
                var access = client.level.registryAccess();
                source.set(DataComponents.TRIM, new ArmorTrim(access.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.DIAMOND),
                        access.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
                source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            var stand = new ArmorStand(client.level, 0, 80, 0);
            stand.setItemSlot(EquipmentSlot.LEGS, source.copy());
            var state = (ArmorStandRenderState) client.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand, 0);
            var control = new GeometryCapture();
            java.util.function.BiFunction nativeModel = (s, slot) -> rig;
            require(GeoArmorRenderer.tryRenderGeoArmorPiece(nativeModel, new PoseStack(), control, source,
                    EquipmentSlot.LEGS, 15728880, (ArmorStandRenderState & GeoRenderState) state), "Decorated native control missing");
            var full = new GeometryCapture();
            NativeExoBridge.nativePass(source, rig, state, new PoseStack(), full, 15728880, 0, null);
            require(control.geometry.size() == full.geometry.size() && vertices(control).equals(vertices(full)),
                    "Native decorated source and independent state differ");
            for (int index = 0; index < control.geometry.size(); index++) {
                require(control.geometry.get(index).type() == full.geometry.get(index).type(), "Native material changed");
            }
            if (decorated) {
                require(passes == control.geometry.size(), "Native decoration added an unexpected pass; investigate before claiming parity");
            } else {
                passes = control.geometry.size();
            }
        }
        return "native.decoratedPassCount=" + passes + "\nnative.noAdditionalEquippedTrimOrFoilPass=true\n";
    }

    private static List<Vertex> sorted(List<Vertex> vertices) {
        return vertices.stream().sorted(Comparator.comparing(Vertex::toString)).toList();
    }

    private static List<Vertex> vertices(GeometryCapture capture) {
        var recorder = new Recorder();
        for (var node : capture.geometry) {
            node.renderer().render(node.pose(), recorder);
        }
        return List.copyOf(recorder.vertices);
    }

    private static void screenshot(Minecraft client, String name) {
        captured = false;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(report(name));
                captured = true;
            } catch (Throwable failure) {
                captureFailure = failure;
            }
        });
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    private static final class Scene extends Screen {
        private final List<ArmorStand> stands = new ArrayList<>();
        private final List<String> labels = List.of("Native Exo", "Exo / iron", "Iron / Exo", "Exo / Exo",
                "Native Exo posed", "Exo / iron posed", "Iron / Exo posed", "Exo / Exo posed");
        private int frames;

        Scene(Minecraft client) {
            super(Component.literal("Oritech native-source spike"));
            var pair = MixedLeggingsFactory.createPair(exo(), new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
            var both = MixedLeggingsFactory.createPair(exo(), exo(), CompositionRules.defaults()).primary();
            var items = List.of(exo(), pair.primary(), pair.inverse(), both);
            for (int index = 0; index < 8; index++) {
                var stand = new ArmorStand(client.level, 0, 80, 0);
                stand.setItemSlot(EquipmentSlot.LEGS, items.get(index % 4).copy());
                if (index >= 4) {
                    stand.setLeftLegPose(new Rotations(-35, 10, -10));
                    stand.setRightLegPose(new Rotations(25, -10, 12));
                }
                if (Boolean.getBoolean("leggings.test.boots")) {
                    stand.setItemSlot(EquipmentSlot.FEET, BootsCoexistence.mixed());
                }
                stands.add(stand);
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            int cellWidth = width / 4;
            int cellHeight = height / 2;
            for (int index = 0; index < stands.size(); index++) {
                var stand = stands.get(index);
                var state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand, 0);
                int x = index % 4 * cellWidth;
                int y = index / 4 * cellHeight;
                graphics.text(font, labels.get(index), x + 5, y + 5, 0xFFFFFFFF, false);
                graphics.entity(state, 65, new Vector3f(0, 1.05F, 0), new Quaternionf().rotateZ((float) Math.PI),
                        new Quaternionf(), x + 4, y + 18, x + cellWidth - 4, y + cellHeight - 4);
            }
            frames++;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz,
                           int color, int light, int overlay) {
    }

    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                               float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz, color, light, overlay));
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
