package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsGuiRenderState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class ItemRenderingClientGameTest implements FabricClientGameTest {
    private static final float EPSILON = 0.0001F;
    private static final int CLIPPING_X = 40;
    private static final int CLIPPING_Y = 40;
    private static final int CONTROL_X = 64;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            configureWorld(singleplayer);
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(ItemRenderingClientGameTest::hasBothOrientations);

            context.runOnClient(ItemRenderingClientGameTest::verifyRenderStates);
            verifyGuiClipping(context);
            captureGuiAndHandContexts(context);
            captureWorldContexts(context, singleplayer);
            verifyAfterResourceReload(context);
        }
    }

    private static void verifyGuiClipping(ClientGameTestContext context) {
        ItemStack visualStack = createClippingPair(false);
        verifyGuiClipping(context, visualStack, false);
        Path screenshot = context.takeScreenshot("exploration2-gui-clipping");
        AtomicInteger guiScale = new AtomicInteger();
        context.runOnClient(client -> guiScale.set(client.getWindow().getGuiScale()));
        assertClippedPixels(screenshot, guiScale.get());

        ItemStack glintStack = createClippingPair(true);
        verifyGuiClipping(context, glintStack, true);

        context.runOnClient(client -> GuiItemSubmissionProbe.arm(CLIPPING_X, CLIPPING_Y));
        context.setScreen(() -> new ClippingTestScreen(glintStack, true));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> assertGuiSubmissions(GuiItemSubmissionProbe.take(), true, true));

        context.runOnClient(client -> GuiItemSubmissionProbe.arm(CONTROL_X, CLIPPING_Y, 1));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            GuiItemRenderState control = GuiItemSubmissionProbe.take().getFirst();
            if (control.scissorArea() != null) {
                throw new AssertionError("GUI clipping leaked its scissor to a later vanilla item: "
                        + control.scissorArea());
            }
        });

        context.setScreen(() -> null);
        context.waitForScreen(null);
    }

    private static void verifyGuiClipping(
            ClientGameTestContext context, ItemStack stack, boolean expectedFoil) {
        context.runOnClient(client -> GuiItemSubmissionProbe.arm(CLIPPING_X, CLIPPING_Y));
        context.setScreen(() -> new ClippingTestScreen(stack, false));
        context.waitForScreen(ClippingTestScreen.class);
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> assertGuiSubmissions(
                GuiItemSubmissionProbe.take(), false, expectedFoil));
        context.waitTicks(2);
    }

    private static ItemStack createClippingPair(boolean foil) {
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        left.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("mismatchedboots-test", "clipping_red"));
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        right.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("mismatchedboots-test", "clipping_blue"));
        ItemStack mixed = MixedBootsFactory.createPair(left, right).primary();
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return mixed;
    }

    private static void assertGuiSubmissions(
            List<GuiItemRenderState> submissions, boolean nested, boolean expectedFoil) {
        if (submissions.size() != 1
                || !(submissions.getFirst().itemStackRenderState() instanceof MixedBootsGuiRenderState combined)) {
            throw new AssertionError("GUI clipping expected one depth-sharing composition");
        }
        GuiItemRenderState item = submissions.getFirst();
        ScreenRectangle expectedScissor = nested
                ? new ScreenRectangle(CLIPPING_X + 2, CLIPPING_Y, 12, 16) : null;
        if (!java.util.Objects.equals(item.scissorArea(), expectedScissor)
                || item.x() != CLIPPING_X || item.y() != CLIPPING_Y) {
            throw new AssertionError("GUI geometry changed caller coordinates or parent scissor");
        }
        for (int side = 0; side < 2; side++) {
            ItemStackRenderState source = combined.sources().get(side);
            assertSourceOrderSingle(source, side == 0 ? "red_concrete" : "blue_concrete", "GUI geometry");
            assertFoilSingle(source, expectedFoil, "GUI geometry");
            LayerRenderStateAccessor layer = layer(access(source), 0);
            assertNear(layer.mismatchedboots$getLocalTransform().m00(), 1.0F, "GUI geometry scale");
            assertNear(layer.mismatchedboots$getLocalTransform().m30(), 0.0F, "GUI geometry offset");
            for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    float x = quad.position(vertex).x();
                    if ((side == 0 && x > 0.5F + EPSILON) || (side == 1 && x < 0.5F - EPSILON)) {
                        throw new AssertionError("GUI geometry retained the other source's half");
                    }
                }
            }
        }
    }

    private static void assertClippedPixels(Path screenshot, int guiScale) {
        int leftRed = 0;
        int leftBlue = 0;
        int rightRed = 0;
        int rightBlue = 0;
        try (NativeImage image = NativeImage.read(Files.newInputStream(screenshot))) {
            int center = (CLIPPING_X + 8) * guiScale;
            for (int y = CLIPPING_Y * guiScale; y < (CLIPPING_Y + 16) * guiScale; y++) {
                for (int x = CLIPPING_X * guiScale; x < (CLIPPING_X + 16) * guiScale; x++) {
                    int pixel = image.getPixel(x, y);
                    int red = pixel >> 16 & 0xFF;
                    int green = pixel >> 8 & 0xFF;
                    int blue = pixel & 0xFF;
                    boolean redPixel = red > green + 30 && red > blue + 30;
                    boolean bluePixel = blue > red + 30 && blue > green + 30;
                    if (x < center) {
                        leftRed += redPixel ? 1 : 0;
                        leftBlue += bluePixel ? 1 : 0;
                    } else {
                        rightRed += redPixel ? 1 : 0;
                        rightBlue += bluePixel ? 1 : 0;
                    }
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect the GUI clipping screenshot", exception);
        }

        int minimumSourcePixels = 20 * guiScale * guiScale;
        if (leftRed < minimumSourcePixels || rightBlue < minimumSourcePixels) {
            throw new AssertionError("GUI clipping did not preserve both source halves: "
                    + leftRed + " red pixels, " + rightBlue + " blue pixels");
        }
        if (leftBlue != 0 || rightRed != 0) {
            throw new AssertionError("GUI clipping leaked pixels across the center: "
                    + leftBlue + " blue pixels on the left, " + rightRed + " red pixels on the right");
        }
    }

    private static void assertSourceOrderSingle(
            ItemStackRenderState state, String expectedSprite, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        if (stateAccess.mismatchedboots$getActiveLayerCount() != 1) {
            throw new AssertionError(scenario + " expected one source layer but got "
                    + stateAccess.mismatchedboots$getActiveLayerCount());
        }
        assertSprite(layer(stateAccess, 0).mismatchedboots$getQuads(), expectedSprite, scenario);
    }

    private static void assertFoilSingle(ItemStackRenderState state, boolean expected, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        boolean actual = layer(stateAccess, 0).mismatchedboots$getFoilType()
                != ItemStackRenderState.FoilType.NONE;
        if (actual != expected) {
            throw new AssertionError(scenario + " has an incorrect glint state");
        }
    }

    private static void configureWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            MixedBootsPair pair = MixedBootsFactory.createPair(
                    new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
            player.getInventory().setItem(0, pair.primary());
            player.getInventory().setItem(1, pair.inverse());
            player.getInventory().setSelectedSlot(0);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 12.0F);
        });
    }

    private static boolean hasBothOrientations(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        return isOrientation(client.player.getInventory().getItem(0), Items.IRON_BOOTS, Items.GOLDEN_BOOTS)
                && isOrientation(client.player.getInventory().getItem(1), Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
    }

    private static void verifyRenderStates(Minecraft client) {
        MixedBootsPair pair = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        for (ItemDisplayContext displayContext : List.of(
                ItemDisplayContext.GUI,
                ItemDisplayContext.FIXED,
                ItemDisplayContext.GROUND,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            ItemStackRenderState primary = resolve(client, pair.primary(), displayContext);
            assertClipped(primary, "primary " + displayContext);
            assertSourceOrder(primary, "iron_boots", "golden_boots", "primary " + displayContext);

            ItemStackRenderState inverse = resolve(client, pair.inverse(), displayContext);
            assertClipped(inverse, "inverse " + displayContext);
            assertSourceOrder(inverse, "golden_boots", "iron_boots", "inverse " + displayContext);
        }

        ItemStack glint = pair.primary().copy();
        glint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        assertFoil(resolve(client, glint, ItemDisplayContext.GUI), true, "root glint");

        ItemStack sourceGlint = new ItemStack(Items.IRON_BOOTS);
        sourceGlint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStack noGlint = MixedBootsFactory.createPair(sourceGlint, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        noGlint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        assertFoil(resolve(client, noGlint, ItemDisplayContext.GUI), false, "root glint override");
        if (!sourceGlint.hasFoil() || !ModContent.getMixedBootsData(noGlint).orElseThrow()
                .left().source().hasFoil()) {
            throw new AssertionError("Rendering mutated a source stack or its stored snapshot");
        }

        ItemStack alternateModelSource = new ItemStack(Items.IRON_BOOTS);
        alternateModelSource.set(DataComponents.ITEM_MODEL,
                Identifier.withDefaultNamespace("diamond_boots"));
        ItemStackRenderState alternateModel = resolve(client,
                MixedBootsFactory.createPair(alternateModelSource, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                ItemDisplayContext.GUI);
        assertSourceOrder(alternateModel, "diamond_boots", "golden_boots", "source item-model override");

        ItemStack missingSource = new ItemStack(Items.IRON_BOOTS);
        missingSource.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("mismatchedboots", "missing_phase5_source"));
        ItemStackRenderState missing = resolve(client,
                MixedBootsFactory.createPair(missingSource, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                ItemDisplayContext.GUI);
        assertClipped(missing, "missing-model geometry");
        assertSprite(layer(access(missing), 1).mismatchedboots$getQuads(),
                "golden_boots", "missing-model fallback right source");

        ItemStack recursiveModelSource = new ItemStack(Items.IRON_BOOTS);
        recursiveModelSource.set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID);
        ItemStackRenderState recursiveModel = resolve(client,
                MixedBootsFactory.createPair(recursiveModelSource, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                ItemDisplayContext.GUI);
        int recursiveLayers = access(recursiveModel).mismatchedboots$getActiveLayerCount();
        if (recursiveLayers < 2 || recursiveLayers > 4) {
            throw new AssertionError("Recursive model fallback produced an unbounded layer count: " + recursiveLayers);
        }
    }

    private static void captureGuiAndHandContexts(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(5);
        context.takeScreenshot("phase5-first-person-hand");

        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(5);
        context.takeScreenshot("phase5-third-person-hand");

        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitForScreen(InventoryScreen.class);
        context.waitTicks(3);
        context.takeScreenshot("phase5-inventory");

        context.setScreen(() -> null);
        context.waitForScreen(null);
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void captureWorldContexts(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        AtomicInteger droppedId = new AtomicInteger(-1);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setSelectedSlot(2);
            Vec3 look = player.getLookAngle();
            ServerLevel level = player.level();
            ItemStack mixed = MixedBootsFactory.createPair(
                    new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).primary();
            ItemEntity dropped = new ItemEntity(level,
                    player.getX() + look.x * 3.0,
                    player.getEyeY() - 0.6,
                    player.getZ() + look.z * 3.0,
                    mixed);
            dropped.setNoGravity(true);
            dropped.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(dropped);
            droppedId.set(dropped.getId());
        });
        context.waitFor(client -> client.level != null
                && client.level.getEntity(droppedId.get()) instanceof ItemEntity);
        context.waitFor(client -> isFacingCamera(client, droppedId.get()), 200);
        context.takeScreenshot("phase5-ground-item");

        AtomicInteger frameId = new AtomicInteger(-1);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            if (level.getEntity(droppedId.get()) instanceof ItemEntity dropped) {
                dropped.discard();
            }
            BlockPos center = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ() + 4.0);
            BlockPos support = center.relative(Direction.WEST);
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(center.relative(Direction.EAST), Blocks.STONE.defaultBlockState());
            ItemFrame frame = new ItemFrame(level, support.relative(Direction.NORTH), Direction.NORTH);
            frame.setItem(MixedBootsFactory.createPair(
                    new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.IRON_BOOTS)).primary());
            level.addFreshEntity(frame);
            ItemFrame control = new ItemFrame(
                    level, center.relative(Direction.EAST).relative(Direction.NORTH), Direction.NORTH);
            control.setItem(new ItemStack(Items.DIAMOND_BOOTS));
            level.addFreshEntity(control);
            frameId.set(frame.getId());
        });
        context.waitFor(client -> client.level != null
                && client.level.getEntity(frameId.get()) instanceof ItemFrame frame
                && ModContent.isMixedBoots(frame.getItem()));
        context.runOnClient(client -> assertItemFrameRenderState(client, frameId.get()));
        context.waitTicks(5);
        context.takeScreenshot("phase5-item-frame");
    }

    @SuppressWarnings("unchecked")
    private static void assertItemFrameRenderState(Minecraft client, int entityId) {
        ItemFrame frame = (ItemFrame) client.level.getEntity(entityId);
        ItemFrameRenderer<ItemFrame> renderer = (ItemFrameRenderer<ItemFrame>)
                client.getEntityRenderDispatcher().getRenderer(frame);
        ItemFrameRenderState state = renderer.createRenderState();
        renderer.extractRenderState(frame, state, 0.0F);
        assertClipped(state.item, "item-frame render state");
        assertSourceOrder(state.item, "golden_boots", "iron_boots", "item-frame render state");
    }

    private static boolean isFacingCamera(Minecraft client, int entityId) {
        return client.level != null
                && client.level.getEntity(entityId) instanceof ItemEntity dropped
                && Math.abs(Mth.wrapDegrees(dropped.getVisualRotationYInDegrees())) < 8.0F;
    }

    private static void verifyAfterResourceReload(ClientGameTestContext context) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        context.waitFor(client -> reload.get() != null && reload.get().isDone(), 1200);
        context.runOnClient(client -> {
            reload.get().join();
            ItemStack mixed = MixedBootsFactory.createPair(
                    new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).primary();
            assertClipped(resolve(client, mixed, ItemDisplayContext.GUI), "post-reload render");
        });
    }

    private static ItemStackRenderState resolve(
            Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, displayContext, client.level, client.player, 0);
        return state;
    }

    private static void assertClipped(ItemStackRenderState state, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        if (stateAccess.mismatchedboots$getActiveLayerCount() != 2) {
            throw new AssertionError(scenario + " expected two clipped layers but got "
                    + stateAccess.mismatchedboots$getActiveLayerCount());
        }
        for (int index = 0; index < 2; index++) {
            LayerRenderStateAccessor layer = layer(stateAccess, index);
            Matrix4f transform = layer.mismatchedboots$getLocalTransform();
            if (!transform.equals(new Matrix4f(), EPSILON)) {
                throw new AssertionError(scenario + " retained a side-by-side transform on layer " + index);
            }
            for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    float canonicalX = transform.transformPosition(
                            quad.position(vertex), new org.joml.Vector3f()).x - 0.5F;
                    if ((index == 0 && canonicalX > EPSILON)
                            || (index == 1 && canonicalX < -EPSILON)) {
                        throw new AssertionError(scenario + " crossed the center on layer " + index);
                    }
                }
            }
        }
    }

    private static void assertSourceOrder(
            ItemStackRenderState state, String expectedLeft, String expectedRight, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        assertSprite(layer(stateAccess, 0).mismatchedboots$getQuads(), expectedLeft, scenario + " left source");
        assertSprite(layer(stateAccess, 1).mismatchedboots$getQuads(), expectedRight, scenario + " right source");
    }

    private static void assertSprite(List<BakedQuad> quads, String expectedPathPart, String scenario) {
        boolean found = quads.stream()
                .map(quad -> quad.materialInfo().sprite().contents().name().getPath())
                .anyMatch(path -> path.contains(expectedPathPart));
        if (!found) {
            throw new AssertionError(scenario + " did not use " + expectedPathPart);
        }
    }

    private static void assertFoil(ItemStackRenderState state, boolean expected, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        int layerCount = stateAccess.mismatchedboots$getActiveLayerCount();
        if (layerCount != 2) {
            throw new AssertionError(scenario + " expected two render layers but got " + layerCount);
        }
        for (int index = 0; index < layerCount; index++) {
            boolean actual = layer(stateAccess, index).mismatchedboots$getFoilType()
                    != ItemStackRenderState.FoilType.NONE;
            if (actual != expected) {
                throw new AssertionError(scenario + " was not projected to layer " + index);
            }
        }
    }

    private static ItemStackRenderStateAccessor access(ItemStackRenderState state) {
        return (ItemStackRenderStateAccessor) (Object) state;
    }

    private static void assertNear(float actual, float expected, String scenario) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(scenario + " expected " + expected + " but got " + actual);
        }
    }

    private static LayerRenderStateAccessor layer(ItemStackRenderStateAccessor state, int index) {
        return (LayerRenderStateAccessor) (Object) state.mismatchedboots$getLayers()[index];
    }

    private static boolean isOrientation(
            ItemStack stack, net.minecraft.world.item.Item left, net.minecraft.world.item.Item right) {
        return ModContent.getMixedBootsData(stack)
                .filter(data -> data.left().source().is(left) && data.right().source().is(right))
                .isPresent();
    }

    private static final class ClippingTestScreen extends Screen {
        private final ItemStack stack;
        private final boolean nested;

        private ClippingTestScreen(ItemStack stack, boolean nested) {
            super(Component.literal("MismatchedBoots GUI clipping test"));
            this.stack = stack;
            this.nested = nested;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (nested) {
                graphics.enableScissor(CLIPPING_X + 2, CLIPPING_Y, CLIPPING_X + 14, CLIPPING_Y + 16);
            }
            try {
                graphics.item(stack, CLIPPING_X, CLIPPING_Y);
            } finally {
                if (nested) {
                    graphics.disableScissor();
                }
            }
            graphics.item(new ItemStack(Items.DIAMOND_BOOTS), CONTROL_X, CLIPPING_Y);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
