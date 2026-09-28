package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.client.MismatchedWorkbenchScreen;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class MismatchedWorkbenchClientGameTest implements FabricClientGameTest {
    private static final int PRIMARY_OUTPUT_X = 69;
    private static final int INVERSE_OUTPUT_X = 91;
    private static final int OUTPUT_Y = 66;

    @Override
    public void runTest(ClientGameTestContext context) {
        assertHiddenAndSilent(context);

        AtomicReference<BlockPos> workbenchPos = new AtomicReference<>();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                BlockPos pos = player.blockPosition().relative(Direction.NORTH, 2);
                player.level().setBlock(pos,
                        ModContent.mismatchedWorkbenchBlock().orElseThrow().defaultBlockState(), 3);
                workbenchPos.set(pos.immutable());
            });
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.level != null
                    && workbenchPos.get() != null
                    && client.level.getBlockState(workbenchPos.get())
                    .is(ModContent.mismatchedWorkbenchBlock().orElseThrow()));

            NetworkTrafficProbe.reset();
            openWorkbench(context, workbenchPos.get());
            context.runOnClient(client -> {
                if (clientMenu(client).slots.size() != MismatchedWorkbenchMenu.HOTBAR_END) {
                    throw new AssertionError("Workbench exposes a non-standard player equipment slot");
                }
                if (!(client.gui.screen() instanceof MismatchedWorkbenchScreen screen)
                        || !screen.getTitle().getString().equals("Mismatched Workbench")) {
                    throw new AssertionError("Workbench screen title was not localized");
                }
            });
            assertEmptySlotGlyphs(context);

            setServerInputs(singleplayer, Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
            context.waitFor(client -> hasOrientation(clientMenu(client).getSlot(2).getItem(),
                            Items.IRON_BOOTS, Items.GOLDEN_BOOTS)
                    && hasOrientation(clientMenu(client).getSlot(3).getItem(),
                            Items.GOLDEN_BOOTS, Items.IRON_BOOTS));
            assertPreviewRendering(context);
            assertOccupiedInputGlyphsDisappear(context);
            context.runOnClient(client -> {
                assertTooltipOrientation(client, clientMenu(client).getSlot(2).getItem(),
                        Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
                assertTooltipOrientation(client, clientMenu(client).getSlot(3).getItem(),
                        Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
            });
            context.takeScreenshot("mismatched-workbench-screen");

            closeWorkbench(context);
            context.waitFor(client -> client.player != null
                    && client.player.containerMenu == client.player.inventoryMenu
                    && count(client.player, Items.IRON_BOOTS) == 1
                    && count(client.player, Items.GOLDEN_BOOTS) == 1
                    && countMixed(client.player) == 0);

            singleplayer.getServer().runOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().getInventory().clearContent());
            context.waitFor(client -> client.player != null
                    && count(client.player, Items.IRON_BOOTS) == 0
                    && count(client.player, Items.GOLDEN_BOOTS) == 0);

            openWorkbench(context, workbenchPos.get());
            setServerInputs(singleplayer, Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
            context.waitFor(client -> !clientMenu(client).getSlot(2).getItem().isEmpty());

            context.runOnClient(client -> client.gameMode.handleContainerInput(
                    clientMenu(client).containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player));
            context.waitFor(client -> {
                MismatchedWorkbenchMenu menu = clientMenu(client);
                return menu.isCommitted()
                        && !menu.getSlot(0).isActive()
                        && !menu.getSlot(1).isActive()
                        && !menu.getSlot(3).getItem().isEmpty()
                        && menu.getSlot(3).mayPickup(client.player);
            });

            context.runOnClient(client -> client.gameMode.handleContainerInput(
                    clientMenu(client).containerId, 3, 0, ContainerInput.QUICK_MOVE, client.player));
            context.waitFor(client -> {
                MismatchedWorkbenchMenu menu = clientMenu(client);
                return !menu.isCommitted()
                        && menu.getSlot(2).getItem().isEmpty()
                        && menu.getSlot(3).getItem().isEmpty()
                        && countMixed(client.player) == 2;
            });
            closeWorkbench(context);

            context.runOnClient(client -> {
                if (!NetworkTrafficProbe.events().isEmpty()) {
                    throw new AssertionError(
                            "Workbench interaction sent MismatchedBoots synchronization payloads: "
                                    + NetworkTrafficProbe.events());
                }
                if (client.gui.screen() != null || client.player.containerMenu != client.player.inventoryMenu) {
                    throw new AssertionError("Closing the workbench left a client screen or menu active");
                }
            });
        }
    }

    private static void assertHiddenAndSilent(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
                throw new AssertionError("Client GameTest master volume is not muted");
            }
            if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")
                    && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE)
                    != GLFW.GLFW_FALSE) {
                throw new AssertionError("Client GameTest window is visible");
            }
        });
    }

    private static void openWorkbench(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        context.waitFor(client -> client.gui.screen() instanceof MismatchedWorkbenchScreen
                && client.player.containerMenu instanceof MismatchedWorkbenchMenu);
    }

    private static void closeWorkbench(ClientGameTestContext context) {
        context.runOnClient(client -> client.player.closeContainer());
        context.waitFor(client -> client.gui.screen() == null
                && client.player.containerMenu == client.player.inventoryMenu);
    }

    private static void setServerInputs(TestSingleplayerContext singleplayer, Item left, Item right) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof MismatchedWorkbenchMenu menu)) {
                throw new AssertionError("Server did not open the Mismatched Workbench menu");
            }
            menu.getSlot(0).set(new ItemStack(left));
            menu.getSlot(1).set(new ItemStack(right));
            menu.broadcastChanges();
        });
    }

    private static void assertEmptySlotGlyphs(ClientGameTestContext context) {
        context.runOnClient(client -> GuiSpriteSubmissionProbe.arm());
        context.waitFor(client -> GuiSpriteSubmissionProbe.hasFrames(2));
        context.runOnClient(client -> {
            GuiSpriteSubmissionProbe.Snapshot snapshot = GuiSpriteSubmissionProbe.takeSnapshot();
            assertSprite(snapshot, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, 69, 22,
                    "left boots input glyph");
            assertSprite(snapshot, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, 91, 22,
                    "right boots input glyph");
        });
    }

    private static void assertOccupiedInputGlyphsDisappear(ClientGameTestContext context) {
        context.runOnClient(client -> GuiSpriteSubmissionProbe.arm());
        context.waitFor(client -> GuiSpriteSubmissionProbe.hasFrames(2));
        context.runOnClient(client -> {
            GuiSpriteSubmissionProbe.Snapshot snapshot = GuiSpriteSubmissionProbe.takeSnapshot();
            if (hasSprite(snapshot, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, 69, 22)
                    || hasSprite(snapshot, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, 91, 22)) {
                throw new AssertionError("Boots input glyph rendered over an occupied input slot");
            }
        });
    }

    private static void assertPreviewRendering(ClientGameTestContext context) {
        context.runOnClient(client -> GuiItemSubmissionProbe.armTargets(
                2, 2, PRIMARY_OUTPUT_X, OUTPUT_Y, INVERSE_OUTPUT_X, OUTPUT_Y));
        context.waitFor(client -> GuiItemSubmissionProbe.isComplete());
        context.runOnClient(client -> {
            GuiItemSubmissionProbe.Snapshot snapshot = GuiItemSubmissionProbe.takeSnapshot();
            ItemStack primary = decorationAt(snapshot.decorations(), PRIMARY_OUTPUT_X, OUTPUT_Y);
            ItemStack inverse = decorationAt(snapshot.decorations(), INVERSE_OUTPUT_X, OUTPUT_Y);
            if (!hasOrientation(primary, Items.IRON_BOOTS, Items.GOLDEN_BOOTS)
                    || !hasOrientation(inverse, Items.GOLDEN_BOOTS, Items.IRON_BOOTS)) {
                throw new AssertionError("Rendered output slots did not preserve preview orientation");
            }
        });
    }

    private static ItemStack decorationAt(
            List<GuiItemSubmissionProbe.Decoration> decorations, int x, int y) {
        return decorations.stream()
                .filter(decoration -> decoration.x() == x && decoration.y() == y)
                .map(GuiItemSubmissionProbe.Decoration::stack)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rendered item decoration at " + x + "," + y));
    }

    private static void assertSprite(
            GuiSpriteSubmissionProbe.Snapshot snapshot,
            net.minecraft.resources.Identifier sprite,
            int x,
            int y,
            String description) {
        if (!hasSprite(snapshot, sprite, x, y)) {
            throw new AssertionError("Missing " + description + ": " + snapshot.submissions());
        }
    }

    private static boolean hasSprite(
            GuiSpriteSubmissionProbe.Snapshot snapshot,
            net.minecraft.resources.Identifier sprite,
            int x,
            int y) {
        return snapshot.submissions().stream().anyMatch(submission ->
                submission.sprite().equals(sprite)
                        && submission.x() == x
                        && submission.y() == y
                        && submission.width() == 16
                        && submission.height() == 16);
    }

    private static MismatchedWorkbenchMenu clientMenu(Minecraft client) {
        if (!(client.player.containerMenu instanceof MismatchedWorkbenchMenu menu)) {
            throw new AssertionError("Client does not have a Mismatched Workbench menu");
        }
        return menu;
    }

    private static boolean hasOrientation(ItemStack stack, Item left, Item right) {
        return ModContent.getMixedBootsData(stack)
                .filter(data -> data.left().source().is(left) && data.right().source().is(right))
                .isPresent();
    }

    private static void assertTooltipOrientation(
            Minecraft client, ItemStack stack, Item left, Item right) {
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        if (!hasTooltipLine(tooltip, "tooltip.mismatchedboots.mixed_boots.left", left.getDescriptionId())
                || !hasTooltipLine(tooltip,
                "tooltip.mismatchedboots.mixed_boots.right", right.getDescriptionId())) {
            throw new AssertionError("Natural output tooltip did not identify its orientation: " + tooltip);
        }
    }

    private static boolean hasTooltipLine(
            List<Component> tooltip, String lineKey, String sourceKey) {
        return tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(lineKey)
                && List.of(contents.getArgs()).stream().anyMatch(argument ->
                argument instanceof Component component && containsTranslation(component, sourceKey)));
    }

    private static boolean containsTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)) {
                return true;
            }
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsTranslation(sibling, key));
    }

    private static int count(net.minecraft.world.entity.player.Player player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static int countMixed(net.minecraft.world.entity.player.Player player) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(ModContent::isMixedBoots)
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
