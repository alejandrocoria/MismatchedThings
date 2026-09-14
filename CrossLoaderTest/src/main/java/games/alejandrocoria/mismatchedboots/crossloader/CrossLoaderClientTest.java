package games.alejandrocoria.mismatchedboots.crossloader;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class CrossLoaderClientTest {
    private static final Identifier MOVEMENT_SPEED = Identifier.withDefaultNamespace("movement_speed");
    private static final Identifier DORMANT_ATTRIBUTE = Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean finished;

    private CrossLoaderClientTest() {
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            ticks++;
            assertUnattended(client);
            if (client.level == null || client.player == null) {
                if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Network acceptance client timed out before joining the server");
                }
                return;
            }
            if (client.hasSingleplayerServer()) {
                throw new AssertionError("Network acceptance client unexpectedly opened an integrated server");
            }

            var rules = AttributeRuleState.current();
            if (rules.policyFor(MOVEMENT_SPEED) != AttributePolicy.COMBINE
                    || rules.policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
                if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Client did not receive the server rule snapshot: " + rules);
                }
                return;
            }

            ItemStack received = client.player.getInventory().getNonEquipmentItems().stream()
                    .filter(ModContent::isMixedBoots)
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            if (received.isEmpty()) {
                if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Client did not receive the server-crafted stack");
                }
                return;
            }

            ItemStack preview = MixedBootsFactory.createPair(
                    CrossLoaderTestStacks.firstSource(), CrossLoaderTestStacks.secondSource()).primary();
            if (!preview.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                    .equals(received.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY))) {
                throw new AssertionError("Client recipe preview differs from the server-crafted result");
            }
            String receivedDescription = CrossLoaderTestStacks.describePrimary(received);
            String previewDescription = CrossLoaderTestStacks.describePrimary(preview);
            if (!receivedDescription.equals(previewDescription)) {
                throw new AssertionError("Client preview description differs from server result");
            }

            CrossLoaderTestFiles.write("client-snapshot.txt", CrossLoaderTestFiles.snapshot(rules));
            CrossLoaderTestFiles.write("client-result.txt", receivedDescription);
            CrossLoaderTestFiles.write("client-success.txt",
                    System.getProperty("mismatchedboots.crossloader.loader", "unknown"));
            finished = true;
            client.stop();
        } catch (Throwable throwable) {
            CrossLoaderTestFiles.failure("client", throwable);
            finished = true;
            client.stop();
        }
    }

    private static void assertUnattended(Minecraft client) {
        if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
            throw new AssertionError("Network acceptance client master volume is not muted");
        }
        if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            throw new AssertionError("Network acceptance client window is visible");
        }
    }
}
