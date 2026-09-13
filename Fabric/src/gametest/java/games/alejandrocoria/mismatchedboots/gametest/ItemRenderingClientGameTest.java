package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class ItemRenderingClientGameTest implements FabricClientGameTest {
    private static final float EXPECTED_OFFSET = 0.15F;
    private static final float EPSILON = 0.0001F;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            configureWorld(singleplayer);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(ItemRenderingClientGameTest::hasBothOrientations);

            context.runOnClient(ItemRenderingClientGameTest::verifyRenderStates);
            captureGuiAndHandContexts(context, singleplayer);
            captureWorldContexts(context, singleplayer);
            verifyAfterResourceReload(context);
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
            assertSideBySide(primary, "primary " + displayContext);
            assertSourceOrder(primary, "iron_boots", "golden_boots", "primary " + displayContext);

            ItemStackRenderState inverse = resolve(client, pair.inverse(), displayContext);
            assertSideBySide(inverse, "inverse " + displayContext);
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
        assertSideBySide(missing, "missing-model fallback");
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

    private static void captureGuiAndHandContexts(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.inventoryMenu.getInputGridSlots().get(0).set(new ItemStack(Items.IRON_BOOTS));
            player.inventoryMenu.getInputGridSlots().get(3).set(new ItemStack(Items.GOLDEN_BOOTS));
        });
        context.waitFor(client -> client.player != null
                && ModContent.isMixedBoots(client.player.inventoryMenu.getSlot(0).getItem()));
        context.waitTicks(3);
        context.takeScreenshot("phase5-crafting-result");

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
        assertSideBySide(state.item, "item-frame render state");
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
            assertSideBySide(resolve(client, mixed, ItemDisplayContext.GUI), "post-reload render");
        });
    }

    private static ItemStackRenderState resolve(
            Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, displayContext, client.level, client.player, 0);
        return state;
    }

    private static void assertSideBySide(ItemStackRenderState state, String scenario) {
        ItemStackRenderStateAccessor stateAccess = access(state);
        if (stateAccess.mismatchedboots$getActiveLayerCount() != 2) {
            throw new AssertionError(scenario + " expected two render layers but got "
                    + stateAccess.mismatchedboots$getActiveLayerCount());
        }
        Matrix4f left = layer(stateAccess, 0).mismatchedboots$getLocalTransform();
        Matrix4f right = layer(stateAccess, 1).mismatchedboots$getLocalTransform();
        if (Math.abs(left.m30() + EXPECTED_OFFSET) > EPSILON
                || Math.abs(right.m30() - EXPECTED_OFFSET) > EPSILON) {
            throw new AssertionError(scenario + " has incorrect side offsets: "
                    + left.m30() + ", " + right.m30());
        }
        if (Math.abs(left.m00() - 0.8F) > EPSILON
                || Math.abs(left.m11() - 0.8F) > EPSILON
                || Math.abs(left.m22() - 1.0F) > EPSILON
                || Math.abs(right.m00() - 0.8F) > EPSILON
                || Math.abs(right.m11() - 0.8F) > EPSILON
                || Math.abs(right.m22() - 1.0F) > EPSILON) {
            throw new AssertionError(scenario + " has incorrect source scaling");
        }
        if (Math.abs(left.m00() - 0.8F) > EPSILON
                || Math.abs(left.m11() - 0.8F) > EPSILON
                || Math.abs(left.m22() - 1.0F) > EPSILON
                || Math.abs(right.m00() - 0.8F) > EPSILON
                || Math.abs(right.m11() - 0.8F) > EPSILON
                || Math.abs(right.m22() - 1.0F) > EPSILON) {
            throw new AssertionError(scenario + " has incorrect source scale");
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

    private static LayerRenderStateAccessor layer(ItemStackRenderStateAccessor state, int index) {
        return (LayerRenderStateAccessor) (Object) state.mismatchedboots$getLayers()[index];
    }

    private static boolean isOrientation(
            ItemStack stack, net.minecraft.world.item.Item left, net.minecraft.world.item.Item right) {
        return ModContent.getMixedBootsData(stack)
                .filter(data -> data.left().source().is(left) && data.right().source().is(right))
                .isPresent();
    }
}
