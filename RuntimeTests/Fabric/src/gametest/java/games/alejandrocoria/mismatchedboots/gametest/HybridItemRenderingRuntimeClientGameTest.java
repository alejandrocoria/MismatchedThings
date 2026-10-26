package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class HybridItemRenderingRuntimeClientGameTest implements FabricClientGameTest {
    public static boolean forceVisualFallback;
    private static final float EPSILON = 0.0001F;
    private static final int REPEATED_RESOLUTIONS = 32;
    private static final Identifier RED_MODEL = testId("clipping_red");
    private static final Identifier ROTATED_RED_MODEL = testId("clipping_red_rotated");
    private static final Identifier BLUE_MODEL = testId("clipping_blue");
    private final boolean meshRoute;

    public HybridItemRenderingRuntimeClientGameTest() {
        this(false);
    }

    HybridItemRenderingRuntimeClientGameTest(boolean meshRoute) {
        this.meshRoute = meshRoute;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        MeshTestFixtures.runtimeRoute = meshRoute;
        AtomicReference<CameraType> originalCamera = new AtomicReference<>();
        context.runOnClient(client -> originalCamera.set(client.options.getCameraType()));
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            configureWorld(singleplayer);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.level != null);

            if (Boolean.getBoolean("mismatchedboots.test.fallbackVisualOnly")) {
                verifyOverlappingFallback(context, singleplayer);
                return;
            }
            context.runOnClient(HybridItemRenderingRuntimeClientGameTest::verifyBoundedRepeatedPlanning);
            verifyHandContexts(context, singleplayer);
            AtomicInteger primaryDrop = createWorldFixtures(context, singleplayer);
            verifyDroppedRotations(context, primaryDrop.get());
            addSimultaneousFixtures(singleplayer);
            verifySimultaneousWorldContexts(context);
            verifyResourceReloadWithWorldItems(context);
        } finally {
            forceVisualFallback = false;
            context.runOnClient(client -> client.options.setCameraType(originalCamera.get()));
            ItemLayerResolutionProbe.clear();
            QuadPlanningProbe.clear();
            RuntimeItemSubmissionProbe.clear();
            MeshTestFixtures.clear();
        }
    }

    private static void verifyOverlappingFallback(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // Keep native boot artwork, but declare clipping unsupported to exercise the real fallback.
        context.runOnClient(client -> {
            forceVisualFallback = true;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        for (boolean invalid : List.of(false, true)) {
            context.runOnClient(client -> forceVisualFallback = !invalid);
            for (boolean foil : List.of(false, true)) {
                singleplayer.getServer().runOnServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    MixedBootsPair pair = MixedBootsFactory.createPair(
                            new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
                    ItemStack main = invalid ? invalidMixed() : pair.primary();
                    ItemStack off = invalid ? invalidMixed() : pair.inverse();
                    main.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    off.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    player.getInventory().setItem(0, main);
                    player.getInventory().setSelectedSlot(0);
                    player.setItemSlot(EquipmentSlot.OFFHAND, off);
                });
                int[] settle = {0};
                context.waitFor(client -> ++settle[0] > 30);
                context.runOnClient(client -> RuntimeItemSubmissionProbe.arm("iron_boots", "golden_boots"));
                context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, 2)
                        && RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, 2));
                var snapshot = takeSubmissions(context);
                for (ItemDisplayContext hand : List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                                       ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
                    for (String sprite : List.of("iron_boots", "golden_boots")) {
                        requireFullFallback(snapshot, hand, sprite, foil);
                    }
                }
                context.takeScreenshot("overlapping-fallback-" + (invalid ? "invalid-" : "")
                                               + "both-hands-" + (foil ? "foil" : "plain"));
            }
        }
        context.runOnClient(client -> forceVisualFallback = true);
        context.setScreen(FallbackScreen::new);
        context.waitFor(client -> client.screen instanceof FallbackScreen screen && screen.frames >= 8);
        context.takeScreenshot("overlapping-fallback-gui");
        context.setScreen(() -> null);
    }

    private static ItemStack invalidMixed() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
    }

    private static void requireFullFallback(RuntimeItemSubmissionProbe.Snapshot snapshot,
                                            ItemDisplayContext hand, String sprite, boolean foil) {
        if (snapshot.submissions().stream().noneMatch(submission -> submission.displayContext() == hand
                && submission.sprite().equals(sprite) && submission.minX() < 0.5F - EPSILON
                && submission.maxX() > 0.5F + EPSILON && hasFoil(submission) == foil)) {
            throw new AssertionError("Fallback lost a complete source or its foil in " + hand + ": " + sprite);
        }
    }

    private static final class FallbackScreen extends Screen {
        private final MixedBootsPair pair = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        private int frames;

        private FallbackScreen() {
            super(Component.literal("Overlapping fallback"));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            frames++;
            String[] labels = {"Iron / gold", "Gold / iron", "Native iron", "Native gold", "No data"};
            List<ItemStack> items = List.of(pair.primary(), pair.inverse(),
                                            new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS), invalidMixed());
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < items.size(); column++) {
                    ItemStack item = items.get(column).copy();
                    item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, row == 1);
                    graphics.text(font, labels[column], 20 + column * 80, 20 + row * 110, -1, false);
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(20 + column * 80, 40 + row * 110);
                    // An imperceptible GUI rotation makes screen-scissor fallback unavailable.
                    if (column != 4) {
                        graphics.pose().rotate(0.001F);
                    }
                    graphics.pose().scale(4, 4);
                    graphics.item(item, 0, 0);
                    graphics.pose().popMatrix();
                }
            }
        }
    }

    private static void configureWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            MixedBootsPair pair = coloredPair(true);
            player.getInventory().setItem(0, pair.primary());
            player.getInventory().setSelectedSlot(0);
            player.setItemSlot(EquipmentSlot.OFFHAND, pair.inverse());
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 10.0F);
        });
    }

    private static void verifyBoundedRepeatedPlanning(Minecraft client) {
        ItemStack red = modelBoot(Items.IRON_BOOTS, RED_MODEL);
        ItemStack blue = modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL);
        int quadsPerResolution = quadCount(resolve(client, red, ItemDisplayContext.FIXED))
                + quadCount(resolve(client, blue, ItemDisplayContext.FIXED));
        if (quadsPerResolution == 0) {
            throw new AssertionError("The runtime planning fixtures contain no quads");
        }

        ItemLayerResolutionProbe.arm(Set.of(RED_MODEL, BLUE_MODEL));
        QuadPlanningProbe.arm();
        ItemStack mixed = MixedBootsFactory.createPair(red, blue).primary();
        try {
            for (int index = 0; index < REPEATED_RESOLUTIONS; index++) {
                assertClipped(resolve(client, mixed, ItemDisplayContext.FIXED), "repeated resolution " + index);
            }
            if (ItemLayerResolutionProbe.count(RED_MODEL) != REPEATED_RESOLUTIONS
                    || ItemLayerResolutionProbe.count(BLUE_MODEL) != REPEATED_RESOLUTIONS) {
                throw new AssertionError("A repeated render resolved a source more than once: red="
                                                 + ItemLayerResolutionProbe.count(RED_MODEL) + ", blue="
                                                 + ItemLayerResolutionProbe.count(BLUE_MODEL));
            }

            int plannedQuads = QuadPlanningProbe.take();
            int expectedQuads = REPEATED_RESOLUTIONS * quadsPerResolution;
            if (plannedQuads != expectedQuads) {
                throw new AssertionError("Repeated rendering planned " + plannedQuads
                                                 + " quads instead of the bounded " + expectedQuads);
            }
        } finally {
            ItemLayerResolutionProbe.clear();
            QuadPlanningProbe.clear();
        }

        for (ItemDisplayContext displayContext : Arrays.stream(ItemDisplayContext.values())
                                                       .filter(value -> value != ItemDisplayContext.GUI)
                                                       .toList()) {
            assertClipped(resolve(client, mixed, displayContext), "runtime context " + displayContext);
        }
    }

    private static void verifyHandContexts(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.waitFor(client -> client.player != null
                && isColoredPair(client.player.getMainHandItem())
                && isColoredPair(client.player.getOffhandItem()));

        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            RuntimeItemSubmissionProbe.arm("red_concrete", "blue_concrete");
        });
        context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, 2)
                && RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, 2));
        context.takeScreenshot("hybrid-first-person-both-hands");
        RuntimeItemSubmissionProbe.Snapshot firstPerson = takeSubmissions(context);
        assertClippedOrientation(firstPerson, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                 "red_concrete", "blue_concrete", true, "first-person main hand");
        assertClippedOrientation(firstPerson, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                                 "blue_concrete", "red_concrete", true, "first-person offhand");

        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            RuntimeItemSubmissionProbe.arm("red_concrete", "blue_concrete");
        });
        context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, 2)
                && RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, 2));
        context.takeScreenshot("hybrid-third-person-both-hands");
        RuntimeItemSubmissionProbe.Snapshot thirdPerson = takeSubmissions(context);
        assertClippedOrientation(thirdPerson, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                                 "red_concrete", "blue_concrete", true, "third-person main hand");
        assertClippedOrientation(thirdPerson, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                                 "blue_concrete", "red_concrete", true, "third-person offhand");

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setSelectedSlot(2);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        });
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitFor(client -> client.player != null
                && client.player.getMainHandItem().isEmpty()
                && client.player.getOffhandItem().isEmpty());
    }

    private static AtomicInteger createWorldFixtures(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        AtomicInteger primaryDrop = new AtomicInteger(-1);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            ItemEntity dropped = addDroppedItem(level,
                                                player.getX(), player.getEyeY() - 0.6, player.getZ() + 3.0,
                                                coloredPair(false).primary());
            primaryDrop.set(dropped.getId());
        });
        context.waitFor(client -> client.level != null
                && client.level.getEntity(primaryDrop.get()) instanceof ItemEntity);
        return primaryDrop;
    }

    private static void verifyDroppedRotations(ClientGameTestContext context, int entityId) {
        context.runOnClient(client -> RuntimeItemSubmissionProbe.arm("red_concrete", "blue_concrete"));
        context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.GROUND, 2));
        RuntimeItemSubmissionProbe.Snapshot snapshot = takeSubmissions(context);
        assertClippedOrientation(snapshot, ItemDisplayContext.GROUND,
                                 "red_concrete", "blue_concrete", false, "rotating ground item");

        for (int angle : List.of(0, 55, 125)) {
            context.waitFor(client -> hasRotation(client, entityId, angle), 400);
            Path screenshot = context.takeScreenshot("hybrid-ground-rotation-" + angle);
            assertColoredPixels(screenshot, "ground rotation " + angle);
        }
    }

    private static void addSimultaneousFixtures(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            addDroppedItem(level, player.getX() - 1.1, player.getEyeY() - 0.6, player.getZ() + 3.2,
                           coloredPair(true).inverse());
            addDroppedItem(level, player.getX() + 1.1, player.getEyeY() - 0.6, player.getZ() + 3.2,
                           coloredPair(false).primary());

            BlockPos center = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ() + 5.0);
            addFrame(level, center.offset(-2, 0, 0), coloredPair(true).primary());
            addFrame(level, center, rotatedPair(true));
            addFrame(level, center.offset(2, 0, 0), dyedPair());
            addFrame(level, center.offset(0, 1, 0), trimmedPair(level));
        });
    }

    private static void verifySimultaneousWorldContexts(ClientGameTestContext context) {
        context.waitFor(HybridItemRenderingRuntimeClientGameTest::hasAllWorldFixtures);
        context.runOnClient(client -> RuntimeItemSubmissionProbe.arm(
                "red_concrete", "blue_concrete", "leather_boots", "diamond_boots"));
        context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.GROUND, 6)
                && RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.FIXED, 6));
        Path screenshot = context.takeScreenshot("hybrid-simultaneous-ground-and-frames");
        assertColoredPixels(screenshot, "simultaneous world items");

        RuntimeItemSubmissionProbe.Snapshot snapshot = takeSubmissions(context);
        assertClippedOrientation(snapshot, ItemDisplayContext.GROUND,
                                 "red_concrete", "blue_concrete", false, "simultaneous primary ground item");
        assertClippedOrientation(snapshot, ItemDisplayContext.GROUND,
                                 "blue_concrete", "red_concrete", true, "simultaneous inverse ground item");
        assertClippedOrientation(snapshot, ItemDisplayContext.FIXED,
                                 "red_concrete", "blue_concrete", true, "compatible item frame");
        assertRotatedClipping(snapshot, ItemDisplayContext.FIXED,
                              "red_concrete", "blue_concrete", true, "rotated item frame");
        assertClippedOrientation(snapshot, ItemDisplayContext.FIXED,
                                 "leather_boots", "diamond_boots", false, "dyed leather item frame");
    }

    private static void verifyResourceReloadWithWorldItems(ClientGameTestContext context) {
        AtomicReference<Map<Integer, ItemStack>> stackSnapshot = new AtomicReference<>();
        context.runOnClient(client -> stackSnapshot.set(snapshotWorldItems(client)));
        if (stackSnapshot.get().isEmpty()) {
            throw new AssertionError("Resource-reload fixture contained no mixed world items");
        }

        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        context.waitFor(client -> reload.get() != null && reload.get().isDone(), 1200);
        context.runOnClient(client -> {
            reload.get().join();
            assertWorldItemsUnchanged(client, stackSnapshot.get());
        });
        // The reload future completes before Minecraft finishes fading its loading overlay.
        context.waitTicks(40);
        context.runOnClient(client -> RuntimeItemSubmissionProbe.arm(
                "red_concrete", "blue_concrete", "leather_boots", "diamond_boots"));
        context.waitFor(client -> RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.GROUND, 6)
                && RuntimeItemSubmissionProbe.hasAtLeast(ItemDisplayContext.FIXED, 6));
        Path screenshot = context.takeScreenshot("hybrid-world-after-resource-reload");
        assertColoredPixels(screenshot, "world items after resource reload");

        RuntimeItemSubmissionProbe.Snapshot snapshot = takeSubmissions(context);
        assertClippedOrientation(snapshot, ItemDisplayContext.GROUND,
                                 "red_concrete", "blue_concrete", false, "post-reload ground item");
        assertClippedOrientation(snapshot, ItemDisplayContext.FIXED,
                                 "red_concrete", "blue_concrete", true, "post-reload item frame");
        assertRotatedClipping(snapshot, ItemDisplayContext.FIXED,
                              "red_concrete", "blue_concrete", true, "post-reload rotated frame");
        assertClippedOrientation(snapshot, ItemDisplayContext.FIXED,
                                 "leather_boots", "diamond_boots", false, "post-reload dyed leather frame");
    }

    private static Map<Integer, ItemStack> snapshotWorldItems(Minecraft client) {
        if (client.player == null || client.level == null) {
            throw new AssertionError("Cannot snapshot world items without a client world");
        }
        AABB bounds = client.player.getBoundingBox().inflate(10.0);
        Map<Integer, ItemStack> stacks = new LinkedHashMap<>();
        client.level.getEntitiesOfClass(ItemEntity.class, bounds, Entity::isAlive).stream()
                    .filter(entity -> ModContent.isMixedBoots(entity.getItem()))
                    .forEach(entity -> stacks.put(entity.getId(), entity.getItem().copy()));
        client.level.getEntitiesOfClass(ItemFrame.class, bounds, Entity::isAlive).stream()
                    .filter(frame -> ModContent.isMixedBoots(frame.getItem()))
                    .forEach(frame -> stacks.put(frame.getId(), frame.getItem().copy()));
        return Map.copyOf(stacks);
    }

    private static void assertWorldItemsUnchanged(Minecraft client, Map<Integer, ItemStack> expected) {
        Map<Integer, ItemStack> actual = snapshotWorldItems(client);
        if (!actual.keySet().equals(expected.keySet())) {
            throw new AssertionError("Resource reload changed the mixed world-item set: expected "
                                             + expected.keySet() + ", actual " + actual.keySet());
        }
        expected.forEach((entityId, before) -> {
            if (!ItemStack.matches(before, actual.get(entityId))) {
                throw new AssertionError("Resource reload changed mixed stack data for entity " + entityId);
            }
        });
    }

    private static RuntimeItemSubmissionProbe.Snapshot takeSubmissions(ClientGameTestContext context) {
        AtomicReference<RuntimeItemSubmissionProbe.Snapshot> snapshot = new AtomicReference<>();
        context.runOnClient(client -> snapshot.set(RuntimeItemSubmissionProbe.take()));
        if (snapshot.get().overflow()) {
            throw new AssertionError("Runtime item submission recording exceeded its fixed bound");
        }
        for (RuntimeItemSubmissionProbe.Submission submission : snapshot.get().submissions()) {
            float[] pose = new Matrix4f(submission.pose()).get(new float[16]);
            for (float value : pose) {
                if (!Float.isFinite(value)) {
                    throw new AssertionError("A runtime item submission contained a non-finite pose");
                }
            }
        }

        return snapshot.get();
    }

    private static void assertClippedOrientation(
            RuntimeItemSubmissionProbe.Snapshot snapshot,
            ItemDisplayContext context,
            String leftSprite,
            String rightSprite,
            boolean foil,
            String scenario) {
        boolean left = snapshot.submissions().stream().anyMatch(submission -> submission.displayContext() == context
                && submission.sprite().equals(leftSprite)
                && submission.minX() >= 0.5F - EPSILON
                && hasFoil(submission) == foil);
        boolean right = snapshot.submissions().stream().anyMatch(submission -> submission.displayContext() == context
                && submission.sprite().equals(rightSprite)
                && submission.maxX() <= 0.5F + EPSILON
                && hasFoil(submission) == foil);
        if (!left || !right) {
            throw new AssertionError(scenario + " did not submit complementary full-scale halves");
        }
    }

    private static void assertRotatedClipping(
            RuntimeItemSubmissionProbe.Snapshot snapshot,
            ItemDisplayContext context,
            String rotatedSprite,
            String ordinarySprite,
            boolean foil,
            String scenario) {
        boolean rotated = snapshot.submissions().stream().anyMatch(submission -> submission.displayContext() == context
                && submission.sprite().equals(rotatedSprite)
                && submission.triangleCount() > 0
                && hasFoil(submission) == foil);
        boolean ordinary = snapshot.submissions().stream().anyMatch(submission -> submission.displayContext() == context
                && submission.sprite().equals(ordinarySprite)
                && submission.maxX() <= 0.5F + EPSILON
                && hasFoil(submission) == foil);
        if (!rotated || !ordinary) {
            throw new AssertionError(scenario + " did not submit the tessellated rotated half");
        }
    }

    private static boolean hasFoil(RuntimeItemSubmissionProbe.Submission submission) {
        return submission.foilType() != ItemStackRenderState.FoilType.NONE;
    }

    private static boolean hasAllWorldFixtures(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }
        AABB bounds = client.player.getBoundingBox().inflate(10.0);
        long drops = client.level.getEntitiesOfClass(ItemEntity.class, bounds, Entity::isAlive).stream()
                                 .filter(entity -> ModContent.isMixedBoots(entity.getItem()))
                                 .count();
        long frames = client.level.getEntitiesOfClass(ItemFrame.class, bounds, Entity::isAlive).stream()
                                  .filter(frame -> ModContent.isMixedBoots(frame.getItem()))
                                  .count();
        return drops >= 3 && frames >= 4;
    }

    private static boolean hasRotation(Minecraft client, int entityId, float expectedDegrees) {
        return client.level != null
                && client.level.getEntity(entityId) instanceof ItemEntity item
                && Math.abs(Mth.wrapDegrees(item.getVisualRotationYInDegrees() - expectedDegrees)) < 8.0F;
    }

    private static void assertColoredPixels(Path screenshot, String scenario) {
        int redPixels = 0;
        int bluePixels = 0;
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            int maximumY = image.getHeight() * 3 / 4;
            for (int y = 0; y < maximumY; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    int red = pixel >> 16 & 0xFF;
                    int green = pixel >> 8 & 0xFF;
                    int blue = pixel & 0xFF;
                    if (red >= 70 && red <= 210 && green < 85 && blue < 105
                            && red > green + 35 && red > blue + 25) {
                        redPixels++;
                    }
                    if (blue >= 65 && blue <= 190 && red < 105 && green < 115
                            && blue > red + 30 && blue > green + 25) {
                        bluePixels++;
                    }
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect " + scenario + " screenshot", exception);
        }
        if (redPixels < 8 || bluePixels < 8) {
            throw new AssertionError(scenario + " did not retain both controlled source colors: red="
                                             + redPixels + ", blue=" + bluePixels);
        }
    }

    private static ItemEntity addDroppedItem(
            ServerLevel level, double x, double y, double z, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, x, y, z, stack);
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        return item;
    }

    private static void addFrame(ServerLevel level, BlockPos position, ItemStack stack) {
        BlockPos support = position.relative(Direction.SOUTH);
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        ItemFrame frame = new ItemFrame(level, position, Direction.NORTH);
        frame.setItem(stack);
        level.addFreshEntity(frame);
    }

    private static MixedBootsPair coloredPair(boolean foil) {
        MixedBootsPair pair = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, RED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL));
        pair.primary().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        pair.inverse().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return pair;
    }

    private static ItemStack rotatedPair(boolean foil) {
        ItemStack mixed = MixedBootsFactory.createPair(
                modelBoot(Items.IRON_BOOTS, ROTATED_RED_MODEL),
                modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL)).primary();
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return mixed;
    }

    private static ItemStack dyedPair() {
        ItemStack leather = new ItemStack(Items.LEATHER_BOOTS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0xCC3344));
        return MixedBootsFactory.createPair(leather, new ItemStack(Items.DIAMOND_BOOTS)).primary();
    }

    private static ItemStack trimmedPair(ServerLevel level) {
        ItemStack leather = new ItemStack(Items.LEATHER_BOOTS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0xCC3344));
        leather.set(DataComponents.TRIM, new ArmorTrim(
                level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.QUARTZ),
                level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST)));
        ItemStack mixed = MixedBootsFactory.createPair(leather, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return mixed;
    }

    private static ItemStack modelBoot(Item item, Identifier model) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    private static boolean isColoredPair(ItemStack stack) {
        return ModContent.getMixedBootsData(stack).filter(data -> {
            Identifier left = data.left().source().get(DataComponents.ITEM_MODEL);
            Identifier right = data.right().source().get(DataComponents.ITEM_MODEL);
            return Set.of(RED_MODEL, BLUE_MODEL).equals(Set.of(left, right));
        }).isPresent();
    }

    private static ItemStackRenderState resolve(
            Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, displayContext, client.level, client.player, 0x5231);
        return state;
    }

    private static int quadCount(ItemStackRenderState state) {
        ItemStackRenderStateAccessor accessor = access(state);
        int result = 0;
        for (int index = 0; index < accessor.mismatchedboots$getActiveLayerCount(); index++) {
            result += layer(accessor, index).mismatchedboots$getQuads().size();
            var mesh = ((LayerRenderStateExtension) (Object)
                    accessor.mismatchedboots$getLayers()[index]).fabric_getMutableMesh();
            if (mesh != null) {
                result += mesh.size();
            }
        }
        return result;
    }

    private static void assertClipped(ItemStackRenderState state, String scenario) {
        ItemStackRenderStateAccessor accessor = access(state);
        if (accessor.mismatchedboots$getActiveLayerCount() != 2) {
            throw new AssertionError(scenario + " expected two layers");
        }
        for (int index = 0; index < 2; index++) {
            LayerRenderStateAccessor layer = layer(accessor, index);
            var mesh = ((LayerRenderStateExtension) (Object)
                    accessor.mismatchedboots$getLayers()[index]).fabric_getMutableMesh();
            int side = index;
            if (MeshTestFixtures.runtimeRoute && (mesh == null || mesh.size() == 0)) {
                throw new AssertionError(scenario + " did not contain real mesh geometry");
            }
            if (mesh != null) {
                mesh.forEach(quad -> {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        float x = layer.mismatchedboots$getLocalTransform()
                                       .transformPosition(quad.copyPos(vertex, null)).x - 0.5F;
                        if ((side == 0 && x < -EPSILON) || (side == 1 && x > EPSILON)) {
                            throw new AssertionError(scenario + " mesh crossed the canonical center");
                        }
                    }
                });
            }
            for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    float x = layer.mismatchedboots$getLocalTransform()
                                   .transformPosition(quad.position(vertex), new org.joml.Vector3f()).x - 0.5F;
                    if ((index == 0 && x < -EPSILON) || (index == 1 && x > EPSILON)) {
                        throw new AssertionError(scenario + " crossed the canonical center");
                    }
                }
            }
        }
    }

    private static ItemStackRenderStateAccessor access(ItemStackRenderState state) {
        return (ItemStackRenderStateAccessor) (Object) state;
    }

    private static LayerRenderStateAccessor layer(ItemStackRenderStateAccessor state, int index) {
        return (LayerRenderStateAccessor) (Object) state.mismatchedboots$getLayers()[index];
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }
}
