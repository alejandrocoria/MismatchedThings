package games.alejandrocoria.mismatchedleggings.test.aether;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.failed;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.passed;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual dedicated transport; waits for item and attribute packets independently. */
public final class AetherConnectedClient {
    private static int ticks;
    private static int phase;
    private static boolean finished;

    private AetherConnectedClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4800, "Aether connected client timeout at " + phase + ": " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            require(!Files.exists(AetherConnectedChecks.report("server.failure.txt")), "Dedicated server failed");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Connected acceptance used an integrated server");
            if (phase < 3) {
                String name = phase == 0 ? "first" : phase == 1 ? "inverse" : "native";
                if (!Files.exists(AetherConnectedChecks.report(name + ".json"))) { return; }
                var before = AetherConnectedChecks.snapshot(client.player);
                if (!before.equals(AetherConnectedChecks.readSnapshot(name))
                        || AetherConnectedChecks.setCount(client.player) != 3
                        || Math.abs(client.player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - .2) > 1E-6) { return; }
                for (int i = 0; i < 12; i++) {
                    for (var flag : new TooltipFlag[] {TooltipFlag.NORMAL, TooltipFlag.ADVANCED}) {
                        client.player.getInventory().getItem(i).getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                    }
                }
                for (var slot : new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS}) {
                    for (var flag : new TooltipFlag[] {TooltipFlag.NORMAL, TooltipFlag.ADVANCED}) {
                        var lines = client.player.getItemBySlot(slot).getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                        if (phase == 2) {
                            var modifier = client.player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifiers().stream()
                                    .filter(value -> value.id().getNamespace().equals("aether_ii")).findFirst().orElseThrow();
                            var row = Attributes.KNOCKBACK_RESISTANCE.value().toComponent(modifier, flag);
                            require(lines.stream().filter(row::equals).count() == 1,
                                    "Relocated native attribute producer missed/duplicated actual worn " + slot + "/" + flag);
                        }
                    }
                }
                require(before.equals(AetherConnectedChecks.snapshot(client.player)), "Tooltip queries changed restored state");
                Files.writeString(AetherConnectedChecks.report(name + ".ok"), "Complete received roots/history, count=3, native knockback=.2; read-only tooltips; native-family row control=" + (phase == 2) + "\n");
                phase++;
            } else if (Files.exists(AetherConnectedChecks.report("finish.txt"))) {
                passed("client");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            failed("client", failure);
            finished = true;
            client.stop();
        }
    }
}
