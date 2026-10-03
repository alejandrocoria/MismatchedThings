package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsGuiRenderState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.gametest.mixin.LayerRenderStateTestAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class CandidateAGuiClientGameTest implements FabricClientGameTest {
    private static final float EPSILON = 0.0001F;
    private static final int ITEM_SIZE = 16;
    private static final int ITEM_HALF = ITEM_SIZE / 2;
    private static final int ITEM_X = 40;
    private static final int ITEM_Y = 40;
    private static final int CONTROL_X = 72;
    private static final int OWNER_SEED = 0x5A17;
    private static final Identifier RED_MODEL = testId("clipping_red");
    private static final Identifier BLUE_MODEL = testId("clipping_blue");
    private static final Identifier ANIMATED_MODEL = testId("animated_source");
    private static final Identifier OVERSIZED_RED_MODEL = testId("oversized_red");
    private static final Identifier OVERSIZED_BLUE_MODEL = testId("oversized_blue");

    @Override
    public void runTest(ClientGameTestContext context) {
        AtomicInteger originalGuiScale = new AtomicInteger();
        context.runOnClient(client -> originalGuiScale.set(client.options.guiScale().get()));
        try {
            try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
                singleplayer.getConnection().waitForChunksRender();
                context.waitFor(client -> client.player != null && client.level != null);

                verifyForwardedContextPoseAndDecorations(context);
                verifyUniformScaleAndParentScissor(context);
                verifyOrdinaryAndDyedBoots(context);
                verifyRootFoilProjection(context);
                verifyAnimatedAndSpecialModels(context);
                verifyOversizedModels(context);
                verifyAbnormalSourcesDoNotLeakScissors(context);
                verifyRepeatedPairsAndModelIdentities(context);
                verifyUnsupportedPoseFallback(context);
                verifyObliqueGuiGeometry(context);
                context.runOnClient(CandidateAGuiClientGameTest::verifyMeshScissorFallback);
                verifyGuiScales(context);
                verifyVisibleResourceReload(context);
            }
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                GuiItemSubmissionProbe.takeSnapshot();
                GuiItemResolutionProbe.take();
                MeshTestFixtures.clear();
                MeshSubmissionProbe.take();
                ItemLayerResolutionProbe.clear();
                client.options.guiScale().set(originalGuiScale.get());
                client.resizeGui();
            });
        }
    }

    private static void verifyForwardedContextPoseAndDecorations(ClientGameTestContext context) {
        ItemStack mixed = clippingPair(false);
        Transform transform = new Transform(11.0F, 7.0F, 1.5F, 0.75F, 0.0F);

        GuiItemSubmissionProbe.armTargets(2, 1, ITEM_X, ITEM_Y, CONTROL_X, ITEM_Y);
        GuiItemResolutionProbe.arm(2, RED_MODEL, BLUE_MODEL);
        context.setScreen(() -> new CharacterizationScreen(
                mixed, transform, true, true, true, OWNER_SEED, true));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete() && GuiItemResolutionProbe.isComplete());

        context.runOnClient(client -> {
            GuiItemSubmissionProbe.Snapshot snapshot = GuiItemSubmissionProbe.takeSnapshot();
            List<GuiItemResolutionProbe.Resolution> resolutions = GuiItemResolutionProbe.take();
            if (!snapshot.events().equals(List.of(
                    GuiItemSubmissionProbe.EventType.ITEM,
                    GuiItemSubmissionProbe.EventType.DECORATION,
                    GuiItemSubmissionProbe.EventType.ITEM))) {
                throw new AssertionError("GUI item/decorations were submitted in the wrong order: "
                        + snapshot.events());
            }
            if (snapshot.decorations().size() != 1
                    || !ItemStack.matches(snapshot.decorations().getFirst().stack(), mixed)
                    || snapshot.decorations().getFirst().x() != ITEM_X
                    || snapshot.decorations().getFirst().y() != ITEM_Y) {
                throw new AssertionError("The mixed root did not receive exactly one decoration call");
            }

            Matrix3x2f expectedPose = transform.matrix();
            ScreenRectangle parent = transformedRectangle(
                    ITEM_X + 2, ITEM_Y, 12, ITEM_SIZE, expectedPose);
            assertSubmission(snapshot.submissions().get(0), expectedPose, parent,
                    "red_concrete", "context left");
            assertSourceSprite(snapshot.submissions().get(0).itemStackRenderState(), "blue_concrete", "context right");
            assertClippedComposition(snapshot.submissions().get(0));
            assertPose(snapshot.submissions().get(1).pose(), expectedPose, "post-clip control");
            if (snapshot.submissions().get(1).scissorArea() != null) {
                throw new AssertionError("The transformed parent scissor leaked to the control item");
            }

            if (resolutions.size() != 2) {
                throw new AssertionError("Expected two source resolutions but got " + resolutions.size());
            }
            for (GuiItemResolutionProbe.Resolution resolution : resolutions) {
                if (resolution.displayContext() != ItemDisplayContext.GUI
                        || resolution.level() != client.level
                        || resolution.owner() != client.player
                        || resolution.seed() != OWNER_SEED) {
                    throw new AssertionError("Candidate A changed source resolution context: " + resolution);
                }
            }
        });
        closeScreen(context);
    }

    private static void verifyUniformScaleAndParentScissor(ClientGameTestContext context) {
        ItemStack mixed = clippingPair(false);
        Transform transform = new Transform(5.0F, 3.0F, 1.25F, 1.25F, 0.0F);
        GuiItemSubmissionProbe.arm(ITEM_X, ITEM_Y);
        context.setScreen(() -> new CharacterizationScreen(
                mixed, transform, true, false, false, 0, false));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            List<GuiItemRenderState> submissions = GuiItemSubmissionProbe.take();
            Matrix3x2f expectedPose = transform.matrix();
            ScreenRectangle parent = transformedRectangle(
                    ITEM_X + 2, ITEM_Y, 12, ITEM_SIZE, expectedPose);
            assertSubmission(submissions.get(0), expectedPose, parent, "red_concrete", "uniform scale left");
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "blue_concrete", "uniform scale right");
            assertClippedComposition(submissions.get(0));
        });
        closeScreen(context);
    }

    private static void verifyOrdinaryAndDyedBoots(ClientGameTestContext context) {
        GuiItemSubmissionProbe.Snapshot ordinary = renderPair(context, MixedBootsFactory.createPair(
                new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary());
        assertSourceSprite(ordinary.submissions().get(0).itemStackRenderState(), "leather_boots", "leather source");
        assertSourceSprite(ordinary.submissions().get(0).itemStackRenderState(), "diamond_boots", "diamond source");
        assertClippedComposition(ordinary.submissions().getFirst());

        DyedItemColor leftDye = new DyedItemColor(0xCC3344);
        DyedItemColor rightDye = new DyedItemColor(0x3366CC);
        ItemStack left = new ItemStack(Items.LEATHER_BOOTS);
        left.set(DataComponents.DYED_COLOR, leftDye);
        ItemStack right = new ItemStack(Items.LEATHER_BOOTS);
        right.set(DataComponents.DYED_COLOR, rightDye);
        GuiItemSubmissionProbe.Snapshot dyed = renderPair(
                context, MixedBootsFactory.createPair(left, right).primary());
        MixedBootsGuiRenderState composition = composition(dyed.submissions().getFirst());
        assertTint(composition.sources().get(0), leftDye.rgb(), "left dyed leather");
        assertTint(composition.sources().get(1), rightDye.rgb(), "right dyed leather");
    }

    private static void verifyRootFoilProjection(ClientGameTestContext context) {
        ItemStack rootFoil = clippingPair(true);
        assertFoil(renderPair(context, rootFoil).submissions(), true, "root foil enabled");

        ItemStack sourceFoil = modelBoot(Items.IRON_BOOTS, RED_MODEL);
        sourceFoil.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStack rootWithoutFoil = MixedBootsFactory.createPair(
                sourceFoil, modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL)).primary();
        rootWithoutFoil.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        assertFoil(renderPair(context, rootWithoutFoil).submissions(), false, "root foil disabled");

        if (!sourceFoil.hasFoil()
                || !ModContent.getMixedBootsData(rootWithoutFoil).orElseThrow().left().source().hasFoil()) {
            throw new AssertionError("GUI rendering mutated a source glint component");
        }
    }

    private static void verifyAnimatedAndSpecialModels(ClientGameTestContext context) {
        ItemStack animated = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, ANIMATED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL)).primary();
        GuiItemSubmissionProbe.Snapshot animatedSnapshot = renderPair(context, animated);
        if (!animatedSnapshot.submissions().get(0).itemStackRenderState().isAnimated()) {
            throw new AssertionError("The animated source lost its animated render-state flag");
        }

        ItemStack special = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, Identifier.withDefaultNamespace("chest")),
                modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL)).primary();
        GuiItemSubmissionProbe.Snapshot specialSnapshot = showPair(context, special, 2);
        assertScissor(specialSnapshot.submissions().get(0), ITEM_X, ITEM_X + ITEM_HALF, "special left");
        assertScissor(specialSnapshot.submissions().get(1), ITEM_X + ITEM_HALF, ITEM_X + ITEM_SIZE, "special right");
        if (testLayer(specialSnapshot.submissions().get(0).itemStackRenderState(), 0)
                .mismatchedboots$getSpecialRenderer() == null) {
            throw new AssertionError("The special source did not reach a SpecialModelRenderer layer");
        }
        context.waitTicks(2);
        Path screenshot = context.takeScreenshot("candidate-a-special-model");
        assertBothHalvesVisible(screenshot, currentGuiScale(context), "special model");
        closeScreen(context);
    }

    private static void verifyOversizedModels(ClientGameTestContext context) {
        ItemStack oversized = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, OVERSIZED_RED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, OVERSIZED_BLUE_MODEL)).primary();
        GuiItemSubmissionProbe.Snapshot snapshot = showPair(context, oversized);
        for (GuiItemRenderState submission : snapshot.submissions()) {
            if (!submission.itemStackRenderState().isOversizedInGui()
                    || submission.oversizedItemBounds() == null) {
                throw new AssertionError("The explicit oversized fixture did not retain oversized bounds");
            }
        }
        context.waitTicks(2);
        Path screenshot = context.takeScreenshot("candidate-a-oversized-model");
        assertClippedComposition(snapshot.submissions().getFirst());
        assertBothHalvesVisible(screenshot, currentGuiScale(context), "oversized geometry");
        closeScreen(context);
    }

    private static void verifyAbnormalSourcesDoNotLeakScissors(ClientGameTestContext context) {
        ItemStack noModel = new ItemStack(Items.IRON_BOOTS);
        noModel.remove(DataComponents.ITEM_MODEL);
        ItemStack noModelPair = MixedBootsFactory.createPair(noModel, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        GuiItemSubmissionProbe.Snapshot noModelSnapshot = renderPairWithControl(context, noModelPair);
        if (!composition(noModelSnapshot.submissions().getFirst()).sources().get(0).isEmpty()) {
            throw new AssertionError("The model-less source unexpectedly produced geometry");
        }
        assertControlHasNoScissor(noModelSnapshot, "model-less source");

        ItemStack missingModel = new ItemStack(Items.IRON_BOOTS);
        missingModel.set(DataComponents.ITEM_MODEL, testId("missing_candidate_a_source"));
        ItemStack missingPair = MixedBootsFactory.createPair(
                missingModel, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        assertControlHasNoScissor(renderPairWithControl(context, missingPair), "missing model");

        ItemStack malformedRoot = clippingPair(false);
        malformedRoot.remove(DataComponents.ITEM_MODEL);
        malformedRoot.remove(DataComponents.EQUIPPABLE);
        assertControlHasNoScissor(renderPairWithControl(context, malformedRoot), "malformed mixed root");
    }

    private static void verifyRepeatedPairsAndModelIdentities(ClientGameTestContext context) {
        ItemStack first = clippingPair(false);
        ItemStack second = clippingPair(false);
        int secondX = ITEM_X + 40;
        GuiItemSubmissionProbe.armTargets(4, 0,
                ITEM_X, ITEM_Y, secondX, ITEM_Y, secondX + 40, ITEM_Y, secondX + 80, ITEM_Y);
        context.setScreen(() -> new RepeatedPairsScreen(first, second, secondX));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            List<GuiItemRenderState> submissions = GuiItemSubmissionProbe.take();
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "red_concrete", "first pair left");
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "blue_concrete", "first pair right");
            assertSourceSprite(submissions.get(1).itemStackRenderState(), "red_concrete", "second pair left");
            assertSourceSprite(submissions.get(1).itemStackRenderState(), "blue_concrete", "second pair right");
            Object firstIdentity = submissions.get(0).itemStackRenderState().getModelIdentity();
            Object secondIdentity = submissions.get(1).itemStackRenderState().getModelIdentity();
            Object inverseIdentity = submissions.get(2).itemStackRenderState().getModelIdentity();
            Object fullSourceIdentity = submissions.get(3).itemStackRenderState().getModelIdentity();
            if (!Objects.equals(firstIdentity, secondIdentity)
                    || Objects.equals(firstIdentity, inverseIdentity)
                    || Objects.equals(firstIdentity, fullSourceIdentity)
                    || composition(submissions.get(0)).sources().stream()
                    .anyMatch(source -> Objects.equals(firstIdentity, source.getModelIdentity()))) {
                throw new AssertionError("Repeated pairs produced unstable or collapsed model identities");
            }
            if (!Objects.equals(fullSourceIdentity,
                    composition(submissions.get(0)).sources().getFirst().getModelIdentity())) {
                throw new AssertionError("The ordinary source did not exercise the same native model identity");
            }
        });
        context.waitTicks(2);
        Path screenshot = context.takeScreenshot("gui-composition-atlas-identities");
        int scale = currentGuiScale(context);
        assertColoredHalves(screenshot, scale, ITEM_X, ITEM_Y, 0);
        assertColoredHalves(screenshot, scale, secondX, ITEM_Y, 0);
        assertAtlasPixels(screenshot, scale, secondX + 40, true);
        assertAtlasPixels(screenshot, scale, secondX + 80, false);
        closeScreen(context);
    }

    private static void verifyUnsupportedPoseFallback(ClientGameTestContext context) {
        ItemStack mixed = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, RED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, Identifier.withDefaultNamespace("chest"))).primary();
        Transform rotated = new Transform(0.0F, 0.0F, 1.0F, 1.0F, 0.2F);
        GuiItemSubmissionProbe.armTargets(1, 0, ITEM_X, ITEM_Y);
        context.setScreen(() -> new CharacterizationScreen(
                mixed, rotated, false, false, false, 0, true));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            GuiItemRenderState submission = GuiItemSubmissionProbe.take().getFirst();
            if (submission.scissorArea() != null) {
                throw new AssertionError("Unsupported rotated GUI pose did not use the unclipped fallback");
            }
            if (layerCount(submission.itemStackRenderState()) != 2
                    || Math.abs(layer(submission.itemStackRenderState(), 0)
                    .mismatchedboots$getLocalTransform().m00() - 0.8F) > EPSILON) {
                throw new AssertionError("Unsupported rotated GUI pose did not use side-by-side rendering");
            }
        });
        closeScreen(context);
    }

    private static void verifyGuiScales(ClientGameTestContext context) {
        for (int requestedScale : List.of(1, 2)) {
            context.runOnClient(client -> {
                client.options.guiScale().set(requestedScale);
                client.resizeGui();
            });
            context.waitFor(client -> client.getWindow().getGuiScale() == requestedScale);
            GuiItemSubmissionProbe.Snapshot snapshot = showPair(context, clippingPair(false));
            if (snapshot.submissions().getFirst().scissorArea() != null) {
                throw new AssertionError("Compatible GUI geometry unexpectedly used a screen scissor");
            }
            assertClippedComposition(snapshot.submissions().getFirst());
            context.waitTicks(2);
            Path screenshot = context.takeScreenshot("candidate-a-gui-scale-" + requestedScale);
            assertColoredHalves(screenshot, requestedScale, ITEM_X, ITEM_Y, 0);
            closeScreen(context);
        }
    }

    private static void verifyObliqueGuiGeometry(ClientGameTestContext context) {
        Identifier red = testId("mesh_rotated_red");
        Identifier blue = testId("mesh_rotated_blue");
        for (boolean extended : List.of(false, true)) {
            context.runOnClient(client -> {
                MeshTestFixtures.clear();
                if (extended) {
                    MeshTestFixtures.models.put(red, MeshTestFixtures.Mode.ANIMATED);
                    MeshTestFixtures.models.put(blue, MeshTestFixtures.Mode.MESH_ONLY);
                }
            });
            ItemStack left = modelBoot(Items.IRON_BOOTS, red);
            ItemStack right = modelBoot(Items.GOLDEN_BOOTS, blue);
            var pair = MixedBootsFactory.createPair(left, right);
            pair.primary().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            pair.inverse().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            GuiItemSubmissionProbe.Snapshot snapshot = showPair(context, pair.primary());
            context.runOnClient(client -> {
                MixedBootsGuiRenderState combined = composition(snapshot.submissions().getFirst());
                if (extended && !combined.isAnimated()) {
                    throw new AssertionError("The composed GUI state lost surviving Fabric mesh animation");
                }
                assertClippedComposition(snapshot.submissions().getFirst());
                if (snapshot.submissions().getFirst().scissorArea() != null) {
                    throw new AssertionError("Oblique GUI geometry used screen-space ownership");
                }
                MeshSubmissionProbe.arm();
                combined.submit(new PoseStack(), new SubmitNodeStorage(), 0x00F000F0, 0, 0);
                var submissions = MeshSubmissionProbe.take();
                if (submissions.size() != 2) {
                    throw new AssertionError("The composed GUI item did not submit both sources together");
                }
                for (int side = 0; side < 2; side++) {
                    var source = combined.sources().get(side);
                    var sourceLayer = rawLayer(source, 0);
                    var mesh = ((LayerRenderStateExtension) (Object) sourceLayer).fabric_getMutableMesh();
                    var faces = MeshSubmissionProbe.fingerprint(mesh);
                    var quads = layer(source, 0).mismatchedboots$getQuads();
                    long triangles = extended ? faces.stream().filter(face ->
                            face.vertices().get(2).equals(face.vertices().get(3))).count()
                            : quads.stream().filter(quad -> quad.position2().equals(quad.position3())).count();
                    if ((extended ? faces.size() : quads.size()) != 12 || triangles != 4) {
                        throw new AssertionError("Oblique GUI ownership did not keep its characterized 12 primitives/4 triangles");
                    }
                    for (var face : faces) {
                        for (var vertex : face.vertices()) {
                            float x = vertex.position().x;
                            if ((side == 0 && x > 0.5F + EPSILON) || (side == 1 && x < 0.5F - EPSILON)) {
                                throw new AssertionError("GUI mesh retained the other source's half");
                            }
                        }
                    }
                    TrackingItemStackRenderState baseline = new TrackingItemStackRenderState();
                    client.getItemModelResolver().updateForTopItem(baseline,
                            side == 0 ? left : right, ItemDisplayContext.GUI, client.level, null, 0);
                    if (!testLayer(source, 0).mismatchedboots$getItemTransform()
                            .equals(testLayer(baseline, 0).mismatchedboots$getItemTransform())) {
                        throw new AssertionError("Canonical clipping changed the oblique GUI display transform");
                    }
                    MeshSubmissionProbe.arm();
                    baseline.submit(new PoseStack(), new SubmitNodeStorage(), 0x00F000F0, 0, 0);
                    var originalSubmission = MeshSubmissionProbe.take().getFirst();
                    var actual = submissions.get(side);
                    if (!actual.pose().equals(originalSubmission.pose(), EPSILON)
                            || actual.context() != ItemDisplayContext.GUI
                            || actual.foil() != ItemStackRenderState.FoilType.STANDARD) {
                        throw new AssertionError("The shared GUI submission changed pose, context, or root foil");
                    }
                }
                // A rotated caller pose does not change geometry compatibility.
                GuiRenderState gui = new GuiRenderState();
                GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, gui, 0, 0);
                ItemLayerResolutionProbe.arm(Set.of(red, blue));
                graphics.pose().rotate(0.2F);
                graphics.item(pair.primary(), ITEM_X, ITEM_Y);
                graphics.item(pair.inverse(), CONTROL_X, ITEM_Y);
                List<GuiItemRenderState> items = new ArrayList<>();
                gui.forEachItem(items::add);
                if (ItemLayerResolutionProbe.count(red) != 2 || ItemLayerResolutionProbe.count(blue) != 2) {
                    throw new AssertionError("GUI geometry selection re-resolved source models");
                }
                ItemLayerResolutionProbe.clear();
                if (items.size() != 2 || items.stream().anyMatch(item ->
                        !(item.itemStackRenderState() instanceof MixedBootsGuiRenderState))
                        || Objects.equals(items.get(0).itemStackRenderState().getModelIdentity(),
                        items.get(1).itemStackRenderState().getModelIdentity())) {
                    throw new AssertionError("Rotated GUI composition or inverse-pair cache identity collapsed");
                }
            });
            context.waitTicks(2);
            Path screenshot = context.takeScreenshot(extended ? "gui-oblique-fabric-mesh" : "gui-oblique-baked-model");
            assertAtlasPixels(screenshot, currentGuiScale(context), ITEM_X, true);
            closeScreen(context);
        }
        context.runOnClient(client -> MeshTestFixtures.clear());
    }

    private static void verifyMeshScissorFallback(net.minecraft.client.Minecraft client) {
        MeshTestFixtures.clear();
        MeshTestFixtures.models.put(BLUE_MODEL, MeshTestFixtures.Mode.INVALID_LATE);
        try {
            ItemStack mixed = clippingPair(false);
            GuiRenderState gui = new GuiRenderState();
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, gui, 0, 0);
            ItemLayerResolutionProbe.arm(Set.of(RED_MODEL, BLUE_MODEL));
            graphics.enableScissor(ITEM_X + 2, ITEM_Y, ITEM_X + 14, ITEM_Y + ITEM_SIZE);
            graphics.item(mixed, ITEM_X, ITEM_Y);
            graphics.disableScissor();
            graphics.item(new ItemStack(Items.DIAMOND_BOOTS), CONTROL_X, ITEM_Y);
            List<GuiItemRenderState> items = new ArrayList<>();
            gui.forEachItem(items::add);
            if (ItemLayerResolutionProbe.count(RED_MODEL) != 1 || ItemLayerResolutionProbe.count(BLUE_MODEL) != 1) {
                throw new AssertionError("GUI fallback re-resolved its source models");
            }
            ItemLayerResolutionProbe.clear();
            if (items.size() != 3 || items.getLast().scissorArea() != null) {
                throw new AssertionError("Nonplanar GUI mesh did not use two atomic scissors or leaked the parent");
            }
            assertScissor(items.get(0), ITEM_X + 2, ITEM_X + ITEM_HALF, "nonplanar left");
            assertScissor(items.get(1), ITEM_X + ITEM_HALF, ITEM_X + 14, "nonplanar right");
            for (int side = 0; side < 2; side++) {
                TrackingItemStackRenderState baseline = new TrackingItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(baseline,
                        modelBoot(side == 0 ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS,
                                side == 0 ? RED_MODEL : BLUE_MODEL),
                        ItemDisplayContext.GUI, client.level, null, 0);
                ItemStackRenderState actual = items.get(side).itemStackRenderState();
                if (!layer(actual, 0).mismatchedboots$getQuads().equals(layer(baseline, 0).mismatchedboots$getQuads())
                        || !MeshSubmissionProbe.fingerprint(((LayerRenderStateExtension) (Object)
                        rawLayer(actual, 0)).fabric_getMutableMesh()).equals(
                        MeshSubmissionProbe.fingerprint(((LayerRenderStateExtension) (Object)
                                rawLayer(baseline, 0)).fabric_getMutableMesh()))
                        || !layer(actual, 0).mismatchedboots$getLocalTransform()
                        .equals(layer(baseline, 0).mismatchedboots$getLocalTransform(), EPSILON)
                        || !Objects.equals(actual instanceof TrackingItemStackRenderState tracking
                        ? tracking.getModelIdentity() : null, baseline.getModelIdentity())) {
                    throw new AssertionError("Late incompatible mesh partially clipped or transformed a GUI source");
                }
            }
        } finally {
            MeshTestFixtures.clear();
            ItemLayerResolutionProbe.clear();
        }
    }

    private static void verifyVisibleResourceReload(ClientGameTestContext context) {
        ItemStack mixed = clippingPair(false);
        GuiItemSubmissionProbe.Snapshot initial = showPair(context, mixed);
        assertSourceSprite(initial.submissions().get(0).itemStackRenderState(), "red_concrete", "pre-reload left");

        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        context.waitFor(client -> reload.get() != null && reload.get().isDone(), 1200);
        context.runOnClient(client -> {
            reload.get().join();
            GuiItemSubmissionProbe.arm(ITEM_X, ITEM_Y);
        });
        context.waitForScreen(CharacterizationScreen.class);
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            List<GuiItemRenderState> submissions = GuiItemSubmissionProbe.take();
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "red_concrete", "post-reload left");
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "blue_concrete", "post-reload right");
            assertClippedComposition(submissions.getFirst());
        });
        closeScreen(context);
    }

    private static GuiItemSubmissionProbe.Snapshot renderPair(ClientGameTestContext context, ItemStack stack) {
        GuiItemSubmissionProbe.Snapshot snapshot = showPair(context, stack);
        closeScreen(context);
        return snapshot;
    }

    private static GuiItemSubmissionProbe.Snapshot showPair(ClientGameTestContext context, ItemStack stack) {
        return showPair(context, stack, 1);
    }

    private static GuiItemSubmissionProbe.Snapshot showPair(ClientGameTestContext context, ItemStack stack, int count) {
        GuiItemSubmissionProbe.arm(ITEM_X, ITEM_Y, count);
        context.setScreen(() -> new CharacterizationScreen(
                stack, Transform.IDENTITY, false, false, false, 0, false));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        AtomicReference<GuiItemSubmissionProbe.Snapshot> snapshot = new AtomicReference<>();
        context.runOnClient(client -> snapshot.set(GuiItemSubmissionProbe.takeSnapshot()));
        return snapshot.get();
    }

    private static GuiItemSubmissionProbe.Snapshot renderPairWithControl(
            ClientGameTestContext context, ItemStack stack) {
        GuiItemSubmissionProbe.armTargets(2, 0, ITEM_X, ITEM_Y, CONTROL_X, ITEM_Y);
        context.setScreen(() -> new CharacterizationScreen(
                stack, Transform.IDENTITY, false, false, false, 0, true));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        AtomicReference<GuiItemSubmissionProbe.Snapshot> snapshot = new AtomicReference<>();
        context.runOnClient(client -> snapshot.set(GuiItemSubmissionProbe.takeSnapshot()));
        closeScreen(context);
        return snapshot.get();
    }

    private static void closeScreen(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.waitForScreen(null);
    }

    private static void assertSubmission(
            GuiItemRenderState submission,
            Matrix3x2fc expectedPose,
            ScreenRectangle expectedScissor,
            String expectedSprite,
            String scenario) {
        if (submission.x() != ITEM_X || submission.y() != ITEM_Y) {
            throw new AssertionError(scenario + " changed logical item coordinates");
        }
        assertPose(submission.pose(), expectedPose, scenario);
        if (!Objects.equals(submission.scissorArea(), expectedScissor)) {
            throw new AssertionError(scenario + " has incorrect scissor: " + submission.scissorArea()
                    + ", expected " + expectedScissor);
        }
        assertSourceSprite(submission.itemStackRenderState(), expectedSprite, scenario);
    }

    private static void assertPose(Matrix3x2fc actual, Matrix3x2fc expected, String scenario) {
        if (Math.abs(actual.m00() - expected.m00()) > EPSILON
                || Math.abs(actual.m01() - expected.m01()) > EPSILON
                || Math.abs(actual.m10() - expected.m10()) > EPSILON
                || Math.abs(actual.m11() - expected.m11()) > EPSILON
                || Math.abs(actual.m20() - expected.m20()) > EPSILON
                || Math.abs(actual.m21() - expected.m21()) > EPSILON) {
            throw new AssertionError(scenario + " changed the GUI pose: " + actual + ", expected " + expected);
        }
    }

    private static ScreenRectangle transformedRectangle(
            int x, int y, int width, int height, Matrix3x2fc pose) {
        return new ScreenRectangle(x, y, width, height).transformAxisAligned(pose);
    }

    private static void assertScissor(GuiItemRenderState state, int left, int right, String scenario) {
        ScreenRectangle scissor = state.scissorArea();
        if (scissor == null
                || scissor.left() != left
                || scissor.right() != right
                || scissor.top() != ITEM_Y
                || scissor.bottom() != ITEM_Y + ITEM_SIZE) {
            throw new AssertionError(scenario + " has incorrect scissor: " + scissor);
        }
    }

    private static void assertSourceSprite(ItemStackRenderState state, String expected, String scenario) {
        int layerCount = layerCount(state);
        for (int index = 0; index < layerCount; index++) {
            for (BakedQuad quad : layer(state, index).mismatchedboots$getQuads()) {
                if (quad.materialInfo().sprite().contents().name().getPath().contains(expected)) {
                    return;
                }
            }
        }
        throw new AssertionError(scenario + " did not resolve sprite " + expected);
    }

    private static void assertTint(ItemStackRenderState state, int expected, String scenario) {
        IntList tints = testLayer(state, 0).mismatchedboots$getTintLayers();
        int expectedArgb = expected | 0xFF000000;
        if (tints == null || tints.isEmpty() || tints.getInt(0) != expectedArgb) {
            throw new AssertionError(scenario + " has incorrect tint layers: " + tints);
        }
    }

    private static void assertFoil(List<GuiItemRenderState> submissions, boolean expected, String scenario) {
        for (GuiItemRenderState submission : submissions) {
            ItemStackRenderState state = submission.itemStackRenderState();
            int layerCount = layerCount(state);
            for (int index = 0; index < layerCount; index++) {
                boolean actual = layer(state, index).mismatchedboots$getFoilType()
                        != ItemStackRenderState.FoilType.NONE;
                if (actual != expected) {
                    throw new AssertionError(scenario + " was not projected to every source layer");
                }
            }
        }
    }

    private static void assertControlHasNoScissor(
            GuiItemSubmissionProbe.Snapshot snapshot, String scenario) {
        if (snapshot.submissions().size() != 2
                || snapshot.submissions().get(1).scissorArea() != null) {
            throw new AssertionError(scenario + " leaked a scissor to the control item");
        }
    }

    private static void assertBothHalvesVisible(Path screenshot, int scale, String scenario) {
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            int leftVisible = 0;
            int rightVisible = 0;
            int center = (ITEM_X + ITEM_HALF) * scale;
            for (int y = ITEM_Y * scale; y < (ITEM_Y + ITEM_SIZE) * scale; y++) {
                for (int x = ITEM_X * scale; x < (ITEM_X + ITEM_SIZE) * scale; x++) {
                    int color = image.getPixel(x, y);
                    int red = color >> 16 & 0xFF;
                    int green = color >> 8 & 0xFF;
                    int blue = color & 0xFF;
                    boolean visible = Math.abs(red - 32) > 12
                            || Math.abs(green - 32) > 12
                            || Math.abs(blue - 32) > 12;
                    if (visible) {
                        if (x < center) {
                            leftVisible++;
                        } else {
                            rightVisible++;
                        }
                    }
                }
            }
            int minimum = 8 * scale * scale;
            if (leftVisible < minimum || rightVisible < minimum) {
                throw new AssertionError(scenario + " did not render both clipped halves: "
                        + leftVisible + ", " + rightVisible);
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect " + scenario + " screenshot", exception);
        }
    }

    private static void assertColoredHalves(Path screenshot, int scale, int xOrigin, int yOrigin, int padding) {
        int leftRed = 0;
        int rightBlue = 0;
        int misplacedRed = 0;
        int misplacedBlue = 0;
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            int clipLeft = xOrigin * scale;
            int center = (xOrigin + ITEM_HALF) * scale;
            int clipRight = (xOrigin + ITEM_SIZE) * scale;
            for (int y = (yOrigin - padding) * scale;
                 y < (yOrigin + ITEM_SIZE + padding) * scale;
                 y++) {
                for (int x = (xOrigin - padding) * scale;
                     x < (xOrigin + ITEM_SIZE + padding) * scale;
                     x++) {
                    int color = image.getPixel(x, y);
                    int red = color >> 16 & 0xFF;
                    int green = color >> 8 & 0xFF;
                    int blue = color & 0xFF;
                    boolean redPixel = red > green + 30 && red > blue + 30;
                    boolean bluePixel = blue > red + 30 && blue > green + 30;
                    leftRed += redPixel && x >= clipLeft && x < center ? 1 : 0;
                    rightBlue += bluePixel && x >= center && x < clipRight ? 1 : 0;
                    misplacedRed += redPixel && (x < clipLeft || x >= center) ? 1 : 0;
                    misplacedBlue += bluePixel && (x < center || x >= clipRight) ? 1 : 0;
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Candidate A colored screenshot", exception);
        }
        int minimum = 20 * scale * scale;
        if (leftRed < minimum || rightBlue < minimum || misplacedRed != 0 || misplacedBlue != 0) {
            throw new AssertionError("Candidate A colored pixels violated its clipping bounds: left red="
                    + leftRed + ", right blue=" + rightBlue + ", misplaced red=" + misplacedRed
                    + ", misplaced blue=" + misplacedBlue);
        }
    }

    private static int currentGuiScale(ClientGameTestContext context) {
        AtomicInteger scale = new AtomicInteger();
        context.runOnClient(client -> scale.set(client.getWindow().getGuiScale()));
        return scale.get();
    }

    private static void assertAtlasPixels(Path screenshot, int scale, int xOrigin, boolean blueThenRed) {
        int left = 0;
        int right = 0;
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            for (int y = ITEM_Y * scale; y < (ITEM_Y + ITEM_SIZE) * scale; y++) {
                for (int x = xOrigin * scale; x < (xOrigin + ITEM_SIZE) * scale; x++) {
                    int color = image.getPixel(x, y);
                    int red = color >> 16 & 0xFF;
                    int green = color >> 8 & 0xFF;
                    int blue = color & 0xFF;
                    boolean redPixel = red > green + 30 && red > blue + 30;
                    boolean bluePixel = blue > red + 30 && blue > green + 30;
                    if (x < (xOrigin + ITEM_HALF) * scale) left += (blueThenRed ? bluePixel : redPixel) ? 1 : 0;
                    else right += redPixel ? 1 : 0;
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect composed GUI atlas pixels", exception);
        }
        if (left < 8 * scale * scale || right < 8 * scale * scale) {
            throw new AssertionError("The native GUI atlas lost expected ordered colors: " + left + ", " + right);
        }
    }

    private static ItemStack clippingPair(boolean foil) {
        ItemStack mixed = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, RED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL)).primary();
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return mixed;
    }

    private static ItemStack modelBoot(net.minecraft.world.item.Item item, Identifier model) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }

    private static ItemStackRenderStateAccessor access(ItemStackRenderState state) {
        return (ItemStackRenderStateAccessor) (Object) state;
    }

    private static LayerRenderStateAccessor layer(ItemStackRenderState state, int index) {
        return (LayerRenderStateAccessor) (Object) rawLayer(state, index);
    }

    private static LayerRenderStateTestAccessor testLayer(ItemStackRenderState state, int index) {
        return (LayerRenderStateTestAccessor) (Object) rawLayer(state, index);
    }

    private static int layerCount(ItemStackRenderState state) {
        return state instanceof MixedBootsGuiRenderState combined
                ? combined.sources().stream().mapToInt(CandidateAGuiClientGameTest::layerCount).sum()
                : access(state).mismatchedboots$getActiveLayerCount();
    }

    private static ItemStackRenderState.LayerRenderState rawLayer(ItemStackRenderState state, int index) {
        if (state instanceof MixedBootsGuiRenderState combined) {
            int leftCount = layerCount(combined.sources().getFirst());
            return index < leftCount ? rawLayer(combined.sources().getFirst(), index)
                    : rawLayer(combined.sources().getLast(), index - leftCount);
        }
        return access(state).mismatchedboots$getLayers()[index];
    }

    private static MixedBootsGuiRenderState composition(GuiItemRenderState item) {
        if (!(item.itemStackRenderState() instanceof MixedBootsGuiRenderState combined)) {
            throw new AssertionError("Compatible sources were not submitted as one depth-sharing composition");
        }
        return combined;
    }

    private static void assertClippedComposition(GuiItemRenderState item) {
        MixedBootsGuiRenderState combined = composition(item);
        for (int side = 0; side < 2; side++) {
            ItemStackRenderState source = combined.sources().get(side);
            for (int index = 0; index < layerCount(source); index++) {
                LayerRenderStateAccessor layer = layer(source, index);
                if (Math.abs(layer.mismatchedboots$getLocalTransform().m00() - 1.0F) > EPSILON
                        || Math.abs(layer.mismatchedboots$getLocalTransform().m30()) > EPSILON) {
                    throw new AssertionError("GUI geometry used fallback translation/scaling");
                }
                for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
                    for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                        float x = quad.position(vertex).x();
                        if ((side == 0 && x > 0.5F + EPSILON) || (side == 1 && x < 0.5F - EPSILON)) {
                            throw new AssertionError("GUI geometry retained the other source's canonical half");
                        }
                    }
                }
            }
        }
    }

    private record Transform(float translateX, float translateY, float scaleX, float scaleY, float rotation) {
        private static final Transform IDENTITY = new Transform(0.0F, 0.0F, 1.0F, 1.0F, 0.0F);

        Matrix3x2f matrix() {
            return new Matrix3x2f()
                    .translate(translateX, translateY)
                    .scale(scaleX, scaleY)
                    .rotate(rotation);
        }

        void apply(Matrix3x2f pose) {
            pose.translate(translateX, translateY).scale(scaleX, scaleY).rotate(rotation);
        }
    }

    private static final class CharacterizationScreen extends Screen {
        private final ItemStack stack;
        private final Transform transform;
        private final boolean parentScissor;
        private final boolean ownerAware;
        private final boolean decorations;
        private final int seed;
        private final boolean control;

        private CharacterizationScreen(
                ItemStack stack,
                Transform transform,
                boolean parentScissor,
                boolean ownerAware,
                boolean decorations,
                int seed,
                boolean control) {
            super(Component.literal("Candidate A characterization"));
            this.stack = stack;
            this.transform = transform;
            this.parentScissor = parentScissor;
            this.ownerAware = ownerAware;
            this.decorations = decorations;
            this.seed = seed;
            this.control = control;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushMatrix();
            transform.apply(graphics.pose());
            try {
                if (parentScissor) {
                    graphics.enableScissor(ITEM_X + 2, ITEM_Y, ITEM_X + 14, ITEM_Y + ITEM_SIZE);
                }
                try {
                    if (ownerAware) {
                        graphics.item(minecraft.player, stack, ITEM_X, ITEM_Y, seed);
                    } else {
                        graphics.item(stack, ITEM_X, ITEM_Y);
                    }
                    if (decorations) {
                        graphics.itemDecorations(font, stack, ITEM_X, ITEM_Y);
                    }
                } finally {
                    if (parentScissor) {
                        graphics.disableScissor();
                    }
                }
                if (control) {
                    graphics.item(new ItemStack(Items.DIAMOND_BOOTS), CONTROL_X, ITEM_Y);
                }
            } finally {
                graphics.pose().popMatrix();
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final class RepeatedPairsScreen extends Screen {
        private final ItemStack first;
        private final ItemStack second;
        private final int secondX;

        private RepeatedPairsScreen(ItemStack first, ItemStack second, int secondX) {
            super(Component.literal("Candidate A repeated pairs"));
            this.first = first;
            this.second = second;
            this.secondX = secondX;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.item(first, ITEM_X, ITEM_Y);
            graphics.item(second, secondX, ITEM_Y);
            var data = ModContent.getMixedBootsData(first).orElseThrow();
            graphics.item(MixedBootsFactory.createPair(data.right().source(), data.left().source()).primary(),
                    secondX + 40, ITEM_Y);
            ItemStack fullSource = data.left().source().copy();
            fullSource.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, first.hasFoil());
            graphics.item(fullSource, secondX + 80, ITEM_Y);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
