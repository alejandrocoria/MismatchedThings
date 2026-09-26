package games.alejandrocoria.mismatchedboots.runtimetest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class NetworkAcceptanceClientTest {
    private static final Identifier MOVEMENT_SPEED = Identifier.withDefaultNamespace("movement_speed");
    private static final Identifier DORMANT_ATTRIBUTE = Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final Identifier ENCHANTABLE = Identifier.withDefaultNamespace("enchantable");
    private static final Identifier DORMANT_COMPONENT =
            Identifier.fromNamespaceAndPath("futuremod", "dormant_energy");
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean finished;

    private NetworkAcceptanceClientTest() {
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
            var componentRules = ComponentRuleState.current();
            if (rules.policyFor(MOVEMENT_SPEED) != AttributePolicy.COMBINE
                    || rules.policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM
                    || componentRules.durability() != DurabilityPolicy.SEPARATE
                    || componentRules.repairCost() != RepairCostPolicy.OWNER
                    || !ComponentRule.of(ComponentPolicy.OWNER).equals(
                            componentRules.declarations().get(ENCHANTABLE))
                    || !ComponentRule.of(ComponentPolicy.SHARE).equals(
                            componentRules.declarations().get(DORMANT_COMPONENT))
                    || componentRules.components().rules().containsKey(DORMANT_COMPONENT)) {
                if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Client did not receive the server rule snapshots: "
                            + rules + "; " + componentRules);
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
                    NetworkAcceptanceStacks.firstSource(), NetworkAcceptanceStacks.secondSource()).primary();
            if (!preview.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                    .equals(received.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY))) {
                throw new AssertionError("Client recipe preview differs from the server-crafted result");
            }
            String receivedDescription = NetworkAcceptanceStacks.describePrimary(received);
            String previewDescription = NetworkAcceptanceStacks.describePrimary(preview);
            if (!receivedDescription.equals(previewDescription)) {
                throw new AssertionError("Client preview description differs from server result");
            }

            NetworkAcceptanceFiles.write(
                    "client-snapshot.txt", NetworkAcceptanceFiles.snapshot(rules, componentRules));
            NetworkAcceptanceFiles.write("client-result.txt", receivedDescription);
            NetworkAcceptanceFiles.write("client-success.txt",
                    System.getProperty("mismatchedboots.runtimetest.loader", "unknown"));
            finished = true;
            client.stop();
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("client", throwable);
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
