package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Passive ordinary-packet observer. Does not manufacture motion, potion state or ability invocations. */
public final class RemainingLifecycleClient {
    private static int ticks;
    private static int sample;
    private static int phase;
    private static boolean ready;
    private static boolean curing;
    private static boolean finished;
    private static final StringBuilder REPORT = new StringBuilder();

    private RemainingLifecycleClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4000, "Remaining client timeout: " + curing + "/" + sample + "/" + phase + " screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Remaining client must stay hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "Connected remaining server failed");
            if (client.level == null || client.player == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Remaining acceptance requires independent dedicated TCP");
            if (!ready) {
                Files.writeString(StateChecks.report("remaining-client-ready.ok"), "Actual dedicated player loaded\n");
                ready = true;
            }
            if (curing && sample == RemainingLifecycle.CURE_SAMPLES) {
                if (!Files.exists(StateChecks.report("finish.txt"))) { return; }
                Files.writeString(StateChecks.report("remaining-client.txt"), REPORT);
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
                return;
            }
            if (curing) { cure(client); } else { rescue(client); }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void rescue(Minecraft client) throws Exception {
        var state = received(client, "void", phase == 2 ? "used" : "equipment");
        if (state == null) { return; }
        double startY = state.get("startY").getAsDouble();
        if (phase == 0) {
            if (Math.abs(client.player.getY() - 81) > .01 || client.player.hasEffect(MobEffects.LEVITATION)) { return; }
            acknowledge("void", "equipment");
            phase = 1;
        } else if (phase == 1) {
            if (!client.player.hasEffect(MobEffects.LEVITATION) || !client.player.hasEffect(MobEffects.SLOW_FALLING)
                    || client.player.getY() < startY + 2 || client.player.getY() >= client.level.getMinY() + 16) { return; }
            require(client.player.getEffect(MobEffects.LEVITATION).getAmplifier() == 9
                    && client.player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3, "Received native rescue potion parameters differ");
            REPORT.append("Void sample ").append(sample).append(": remote rescue rise=").append(client.player.getY() - startY)
                    .append("; ordinary native Levitation/Slow Falling and complete roots received\n");
            acknowledge("void", "rescue");
            phase = 2;
        } else {
            acknowledge("void", "used");
            phase = 0;
            if (++sample == RemainingLifecycle.voidSamples()) { sample = 0; curing = true; }
        }
    }

    private static void cure(Minecraft client) throws Exception {
        String name = switch (phase) { case 0 -> "idle"; case 1 -> "active"; case 2 -> "returned"; default -> "used"; };
        if (received(client, "cure", name) == null) { return; }
        boolean targetsPresent = phase != 1;
        if (client.player.hasEffect(MobEffects.POISON) != targetsPresent || client.player.hasEffect(MobEffects.WITHER) != targetsPresent
                || !client.player.hasEffect(MobEffects.LUCK)) { return; }
        acknowledge("cure", name);
        if (++phase == 4) {
            REPORT.append("Cure sample ").append(sample).append(": initial idle positives, selected target removals, returned-idle positives;")
                    .append(" Luck and complete root/equipment packets retained\n");
            sample++;
            phase = 0;
        }
    }

    private static JsonObject received(Minecraft client, String family, String name) throws Exception {
        String path = "remaining-" + family + "-" + sample + "." + name;
        if (!Files.exists(StateChecks.report(path + ".ready"))) { return null; }
        var state = JsonParser.parseString(Files.readString(StateChecks.report(path + ".json"))).getAsJsonObject();
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if (!ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("legs")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.LEGS))
                || !ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("feet")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.FEET))) { return null; }
        String slot = state.get("slot").getAsString();
        var actual = slot.equals("INVENTORY") ? client.player.getInventory().getItem(8) : client.player.getItemBySlot(EquipmentSlot.valueOf(slot));
        return ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("root")).getOrThrow(), actual) ? state : null;
    }

    private static void acknowledge(String family, String name) throws Exception {
        Files.writeString(StateChecks.report("remaining-" + family + "-" + sample + "." + name + ".ok"), "Actual remote gameplay accepted\n");
    }
}
