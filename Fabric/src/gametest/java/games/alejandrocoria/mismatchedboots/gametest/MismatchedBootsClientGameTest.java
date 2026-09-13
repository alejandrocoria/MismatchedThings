package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class MismatchedBootsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
                throw new AssertionError("Client GameTest master volume is not muted");
            }
            if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")) {
                int visibility = GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE);
                if (visibility != GLFW.GLFW_FALSE) {
                    throw new AssertionError("Client GameTest window is visible");
                }
            }
            client.getWindow().setWindowed(854, 480);
            client.options.guiScale().set(2);
            client.options.renderDistance().set(6);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runOnServer(server -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                server.getPlayerList().getPlayers().getFirst().setPose(Pose.STANDING);
                ItemStack mixed = MixedBootsFactory.createPair(
                        new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).primary();
                server.getPlayerList().getPlayers().getFirst().getInventory().add(mixed);
            });
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null
                    && client.player.getInventory().contains(
                    MismatchedBootsClientGameTest::hasExpectedSynchronizedData));
            context.runOnClient(client -> {
                List<ItemStack> mixedStacks = client.player.getInventory().getNonEquipmentItems().stream()
                        .filter(ModContent::isMixedBoots)
                        .toList();
                if (mixedStacks.size() != 1 || mixedStacks.getFirst().getCount() != 1) {
                    throw new AssertionError("Expected exactly one synchronized mixed stack: " + mixedStacks);
                }
                if (!hasExpectedSynchronizedData(mixedStacks.getFirst())) {
                    throw new AssertionError("Synchronized mixed stack has incorrect source data");
                }
            });
            context.takeScreenshot("mismatchedboots-client-sentinel");
        }
    }

    private static boolean hasExpectedSynchronizedData(ItemStack stack) {
        return stack.is(ModContent.MIXED_BOOTS_KEY)
                && ModContent.getMixedBootsData(stack)
                        .filter(data -> data.left().source().is(Items.IRON_BOOTS)
                                && data.right().source().is(Items.GOLDEN_BOOTS))
                        .isPresent();
    }
}
