package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Complete received snapshots, never integrated-server access or a substitute packet codec. */
public final class StateClient {
    private static int ticks;
    private static int phase;
    private static boolean finished;
    private static CompletableFuture<Void> reload;

    private StateClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4800, "SG TCP client timeout at " + phase + ": " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "SG client must remain hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "SG dedicated server failed");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "SG synchronization accidentally used integrated server");
            if (phase == 0 && WearerLifecycle.enabled() && StateChecks.stage().equals("reconnect")) { WearerLifecycleClient.rejoined(client); }
            if (phase == 0 && matches(client, "initial")) {
                verify(client);
                Files.writeString(StateChecks.report("client-initial.ok"), "Full native state/projection/history packet matched\n");
                phase = 1;
            } else if (phase == 1 && matches(client, "moved")) {
                verify(client);
                if (!StateChecks.absent()) { StateRendering.verify(client, List.of(client.player.getItemBySlot(EquipmentSlot.LEGS), client.player.getInventory().getItem(1))); }
                reload = client.reloadResourcePacks();
                phase = 2;
            } else if (phase == 2 && reload.isDone() && client.getOverlay() == null) {
                reload.join();
                require(matches(client, "moved"), "Resource reload changed received state");
                verify(client);
                if (!StateChecks.absent()) { StateRendering.verify(client, List.of(client.player.getItemBySlot(EquipmentSlot.LEGS), client.player.getInventory().getItem(1))); }
                Files.writeString(StateChecks.report("client-moved.ok"), "Inventory/equipment moves, read-only queries and resource reload passed\n");
                phase = 3;
            } else if (phase == 3) {
                if (WearerLifecycle.enabled() && !StateChecks.absent()) { WearerLifecycleClient.tick(client); }
                if (!Files.exists(StateChecks.report("finish.txt"))) { return; }
                Files.writeString(StateChecks.report("client.txt"), "Actual hidden/muted dedicated TCP client; complete ordinary inventory/equipment packets, live state/attributes, moves and reconnect stage; native source item/waist/legs/trim/glint submissions before/after resource reload; no pixel/owner acceptance claimed\n");
                finished = true;
                SkeletonChecks.passed("client");
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean matches(Minecraft client, String name) throws Exception {
        return Files.exists(StateChecks.report(name + ".ready"))
                && StateChecks.snapshot(client.player).equals(StateChecks.read(StateChecks.report(name + ".nbt")))
                && equippedMatches(client);
    }

    private static void verify(Minecraft client) {
        for (var root : List.of(client.player.getMainHandItem(), client.player.getOffhandItem(),
                client.player.getItemBySlot(EquipmentSlot.LEGS), client.player.getInventory().getItem(1))) {
            var before = root.copy();
            if (StateChecks.absent()) { StateChecks.inactive(root); }
            else { require(root.has(SilentGearCompatibility.state()), "Bounded owned state missing in actual item packet"); }
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                root.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level), client.player, flag);
                root.getAttributeModifiers();
            }
            require(ItemStack.matches(before, root), "Client queries wrote state or refreshed projection");
        }
        require(equippedMatches(client), "Received equipped armor differs from actual root projection");
    }

    private static boolean equippedMatches(Minecraft client) {
        var worn = client.player.getItemBySlot(EquipmentSlot.LEGS);
        double expected = StateChecks.absent() ? 0 : worn.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
        return Math.abs(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - expected) < 1E-6;
    }
}
