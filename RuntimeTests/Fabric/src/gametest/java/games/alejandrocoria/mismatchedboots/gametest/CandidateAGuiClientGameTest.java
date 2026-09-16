package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.gametest.mixin.LayerRenderStateTestAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
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
import java.util.List;
import java.util.Objects;
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
                singleplayer.getClientLevel().waitForChunksRender();
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
                verifyGuiScales(context);
                verifyVisibleResourceReload(context);
            }
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                GuiItemSubmissionProbe.takeSnapshot();
                GuiItemResolutionProbe.take();
                client.options.guiScale().set(originalGuiScale.get());
                client.resizeGui();
            });
        }
    }

    private static void verifyForwardedContextPoseAndDecorations(ClientGameTestContext context) {
        ItemStack mixed = clippingPair(false);
        Transform transform = new Transform(11.0F, 7.0F, 1.5F, 0.75F, 0.0F);

        GuiItemSubmissionProbe.armTargets(3, 1, ITEM_X, ITEM_Y, CONTROL_X, ITEM_Y);
        GuiItemResolutionProbe.arm(2, RED_MODEL, BLUE_MODEL);
        context.setScreen(() -> new CharacterizationScreen(
                mixed, transform, true, true, true, OWNER_SEED, true));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete() && GuiItemResolutionProbe.isComplete());

        context.runOnClient(client -> {
            GuiItemSubmissionProbe.Snapshot snapshot = GuiItemSubmissionProbe.takeSnapshot();
            List<GuiItemResolutionProbe.Resolution> resolutions = GuiItemResolutionProbe.take();
            if (!snapshot.events().equals(List.of(
                    GuiItemSubmissionProbe.EventType.ITEM,
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
            assertSubmission(snapshot.submissions().get(0), expectedPose,
                    transformedRectangle(ITEM_X, ITEM_Y, ITEM_HALF, ITEM_SIZE, expectedPose)
                            .intersection(parent), "red_concrete", "context left");
            assertSubmission(snapshot.submissions().get(1), expectedPose,
                    transformedRectangle(ITEM_X + ITEM_HALF, ITEM_Y, ITEM_HALF, ITEM_SIZE, expectedPose)
                            .intersection(parent), "blue_concrete", "context right");
            assertPose(snapshot.submissions().get(2).pose(), expectedPose, "post-clip control");
            if (snapshot.submissions().get(2).scissorArea() != null) {
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
            assertSubmission(submissions.get(0), expectedPose,
                    transformedRectangle(ITEM_X, ITEM_Y, ITEM_HALF, ITEM_SIZE, expectedPose)
                            .intersection(parent), "red_concrete", "uniform scale left");
            assertSubmission(submissions.get(1), expectedPose,
                    transformedRectangle(ITEM_X + ITEM_HALF, ITEM_Y, ITEM_HALF, ITEM_SIZE, expectedPose)
                            .intersection(parent), "blue_concrete", "uniform scale right");
        });
        closeScreen(context);
    }

    private static void verifyOrdinaryAndDyedBoots(ClientGameTestContext context) {
        GuiItemSubmissionProbe.Snapshot ordinary = renderPair(context, MixedBootsFactory.createPair(
                new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary());
        assertSourceSprite(ordinary.submissions().get(0).itemStackRenderState(), "leather_boots", "leather source");
        assertSourceSprite(ordinary.submissions().get(1).itemStackRenderState(), "diamond_boots", "diamond source");

        DyedItemColor leftDye = new DyedItemColor(0xCC3344);
        DyedItemColor rightDye = new DyedItemColor(0x3366CC);
        ItemStack left = new ItemStack(Items.LEATHER_BOOTS);
        left.set(DataComponents.DYED_COLOR, leftDye);
        ItemStack right = new ItemStack(Items.LEATHER_BOOTS);
        right.set(DataComponents.DYED_COLOR, rightDye);
        GuiItemSubmissionProbe.Snapshot dyed = renderPair(
                context, MixedBootsFactory.createPair(left, right).primary());
        assertTint(dyed.submissions().get(0).itemStackRenderState(), leftDye.rgb(), "left dyed leather");
        assertTint(dyed.submissions().get(1).itemStackRenderState(), rightDye.rgb(), "right dyed leather");
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
        GuiItemSubmissionProbe.Snapshot specialSnapshot = showPair(context, special);
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
        assertOversizedPixelsStayClipped(screenshot, currentGuiScale(context));
        closeScreen(context);
    }

    private static void verifyAbnormalSourcesDoNotLeakScissors(ClientGameTestContext context) {
        ItemStack noModel = new ItemStack(Items.IRON_BOOTS);
        noModel.remove(DataComponents.ITEM_MODEL);
        ItemStack noModelPair = MixedBootsFactory.createPair(noModel, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        GuiItemSubmissionProbe.Snapshot noModelSnapshot = renderPairWithControl(context, noModelPair);
        if (!noModelSnapshot.submissions().get(0).itemStackRenderState().isEmpty()) {
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
        GuiItemSubmissionProbe.armTargets(4, 0, ITEM_X, ITEM_Y, secondX, ITEM_Y);
        context.setScreen(() -> new RepeatedPairsScreen(first, second, secondX));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            List<GuiItemRenderState> submissions = GuiItemSubmissionProbe.take();
            assertSourceSprite(submissions.get(0).itemStackRenderState(), "red_concrete", "first pair left");
            assertSourceSprite(submissions.get(1).itemStackRenderState(), "blue_concrete", "first pair right");
            assertSourceSprite(submissions.get(2).itemStackRenderState(), "red_concrete", "second pair left");
            assertSourceSprite(submissions.get(3).itemStackRenderState(), "blue_concrete", "second pair right");
            Object firstLeft = submissions.get(0).itemStackRenderState().getModelIdentity();
            Object firstRight = submissions.get(1).itemStackRenderState().getModelIdentity();
            Object secondLeft = submissions.get(2).itemStackRenderState().getModelIdentity();
            Object secondRight = submissions.get(3).itemStackRenderState().getModelIdentity();
            if (!Objects.equals(firstLeft, secondLeft)
                    || !Objects.equals(firstRight, secondRight)
                    || Objects.equals(firstLeft, firstRight)) {
                throw new AssertionError("Repeated pairs produced unstable or collapsed model identities");
            }
        });
        closeScreen(context);
    }

    private static void verifyUnsupportedPoseFallback(ClientGameTestContext context) {
        ItemStack mixed = clippingPair(false);
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
            if (access(submission.itemStackRenderState()).mismatchedboots$getActiveLayerCount() != 2) {
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
            assertScissor(snapshot.submissions().get(0), ITEM_X, ITEM_X + ITEM_HALF, "scale " + requestedScale);
            assertScissor(snapshot.submissions().get(1), ITEM_X + ITEM_HALF, ITEM_X + ITEM_SIZE,
                    "scale " + requestedScale);
            context.waitTicks(2);
            Path screenshot = context.takeScreenshot("candidate-a-gui-scale-" + requestedScale);
            assertColoredHalves(screenshot, requestedScale, ITEM_X, ITEM_Y, 0);
            closeScreen(context);
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
            assertSourceSprite(submissions.get(1).itemStackRenderState(), "blue_concrete", "post-reload right");
        });
        closeScreen(context);
    }

    private static GuiItemSubmissionProbe.Snapshot renderPair(ClientGameTestContext context, ItemStack stack) {
        GuiItemSubmissionProbe.Snapshot snapshot = showPair(context, stack);
        closeScreen(context);
        return snapshot;
    }

    private static GuiItemSubmissionProbe.Snapshot showPair(ClientGameTestContext context, ItemStack stack) {
        GuiItemSubmissionProbe.arm(ITEM_X, ITEM_Y);
        context.setScreen(() -> new CharacterizationScreen(
                stack, Transform.IDENTITY, false, false, false, 0, false));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        AtomicReference<GuiItemSubmissionProbe.Snapshot> snapshot = new AtomicReference<>();
        context.runOnClient(client -> snapshot.set(GuiItemSubmissionProbe.takeSnapshot()));
        return snapshot.get();
    }

    private static GuiItemSubmissionProbe.Snapshot renderPairWithControl(
            ClientGameTestContext context, ItemStack stack) {
        GuiItemSubmissionProbe.armTargets(3, 0, ITEM_X, ITEM_Y, CONTROL_X, ITEM_Y);
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
        int layerCount = access(state).mismatchedboots$getActiveLayerCount();
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
            int layerCount = access(state).mismatchedboots$getActiveLayerCount();
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
        if (snapshot.submissions().size() != 3
                || snapshot.submissions().get(2).scissorArea() != null) {
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

    private static void assertOversizedPixelsStayClipped(Path screenshot, int scale) {
        assertColoredHalves(screenshot, scale, ITEM_X, ITEM_Y, ITEM_SIZE);
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
        return (LayerRenderStateAccessor) (Object) access(state).mismatchedboots$getLayers()[index];
    }

    private static LayerRenderStateTestAccessor testLayer(ItemStackRenderState state, int index) {
        return (LayerRenderStateTestAccessor) (Object) access(state).mismatchedboots$getLayers()[index];
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
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
