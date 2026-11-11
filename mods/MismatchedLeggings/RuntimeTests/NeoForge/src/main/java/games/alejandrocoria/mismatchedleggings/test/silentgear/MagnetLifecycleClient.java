package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonParser;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Passive dedicated TCP observer: no integrated server, velocity edits, teleports or custom movement packets. */
public final class MagnetLifecycleClient {
    private static int ticks;
    private static int step;
    private static boolean moved;
    private static boolean delayObserved;
    private static boolean finished;
    private static boolean ready;
    private static final StringBuilder REPORT = new StringBuilder();

    private MagnetLifecycleClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4800, "Connected magnet client timeout at step " + step + ": " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Magnet client must stay hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "Connected magnet server failed");
            if (client.level == null || client.player == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Magnet acceptance accidentally used integrated server");
            if (!ready) {
                Files.writeString(StateChecks.report("magnet-client-ready.ok"), "Actual dedicated client loaded\n");
                ready = true;
            }
            if (step == MagnetLifecycle.SAMPLES) {
                if (!Files.exists(StateChecks.report("finish.txt"))) { return; }
                Files.writeString(StateChecks.report("magnet-client.txt"), REPORT);
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
                return;
            }
            String prefix = "magnet-" + step;
            if (!Files.exists(StateChecks.report(prefix + ".equipment.ok"))) {
                if (!matches(client, prefix, "equipment") || Math.abs(client.player.getX() - 8.5) > .01
                        || Math.abs(client.player.getY() - 81) > .01) { return; }
                Files.writeString(StateChecks.report(prefix + ".equipment.ok"), "Ordinary root/equipment packets and teleport received\n");
            }
            if (!Files.exists(StateChecks.report(prefix + ".items.json"))) { return; }
            var ids = JsonParser.parseString(Files.readString(StateChecks.report(prefix + ".items.json"))).getAsJsonObject();
            var moving = client.level.getEntity(ids.get("eligible").getAsInt());
            var delayed = client.level.getEntity(ids.get("delayed").getAsInt());
            var rejected = client.level.getEntity(ids.get("rejected").getAsInt());
            if (moving != null && moving.getX() < MagnetLifecycle.START_X - .15) { moved = true; }
            if (delayed != null && Math.abs(delayed.getX() - MagnetLifecycle.START_X) < .01
                    && MagnetLifecycle.count(client.player, Items.GOLD_INGOT) == 0) { delayObserved = true; }
            if (!Files.exists(StateChecks.report(prefix + ".active.ok"))) {
                if (!Files.exists(StateChecks.report(prefix + ".active.settled")) || !moved || !delayObserved
                        || MagnetLifecycle.count(client.player, Items.DIAMOND) != 3 || MagnetLifecycle.count(client.player, Items.GOLD_INGOT) != 4
                        || moving != null || delayed != null || rejected == null) { return; }
                require(MagnetLifecycle.count(client.player, Items.DIRT) == 0 && Math.abs(rejected.getX() - MagnetLifecycle.START_X) < .01,
                        "Client rejected-item control moved or was picked up");
                Files.writeString(StateChecks.report(prefix + ".active.ok"), "Native S2C motion, exact ordinary pickup counts and negative controls received\n");
            }
            if (!Files.exists(StateChecks.report(prefix + ".idle-id.txt")) || !Files.exists(StateChecks.report(prefix + ".idle.settled"))
                    || !matches(client, prefix, "idle")) { return; }
            var idle = client.level.getEntity(Integer.parseInt(Files.readString(StateChecks.report(prefix + ".idle-id.txt"))));
            if (idle == null) { return; }
            require(Math.abs(idle.getX() - MagnetLifecycle.START_X) < .01 && MagnetLifecycle.count(client.player, Items.EMERALD) == 0,
                    "Client observed new attraction while roots were idle");
            REPORT.append(MagnetLifecycle.label(step)).append(": actual TCP motion/pickup, diamond=3 gold=4 dirt=0, delay observed, idle stop, full received root data\n");
            Files.writeString(StateChecks.report(prefix + ".ok"), "Client accepted " + MagnetLifecycle.label(step));
            step++;
            moved = false;
            delayObserved = false;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean matches(Minecraft client, String prefix, String phase) throws Exception {
        return Files.exists(StateChecks.report(prefix + "." + phase + ".ready"))
                && MagnetLifecycle.snapshot(client.player).equals(StateChecks.read(StateChecks.report(prefix + "." + phase + ".nbt")));
    }
}
