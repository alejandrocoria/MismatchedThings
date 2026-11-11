package games.alejandrocoria.mismatchedleggings.test.silentgear;

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

/** Passive remote client; rescue movement and potions come only from normal gameplay packets. */
public final class RemainingSpikeClient {
    private static int ticks;
    private static int sample;
    private static int phase;
    private static double startY;
    private static boolean ready;
    private static boolean finished;
    private static final StringBuilder REPORT = new StringBuilder();

    private RemainingSpikeClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4000, "Remaining client timeout " + sample + "/" + phase + " screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Research client must remain hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "Remaining server failed");
            if (client.level == null || client.player == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Research must use independent dedicated TCP");
            if (!ready) {
                Files.writeString(StateChecks.report("remaining-client-ready.ok"), "Real remote player loaded\n");
                ready = true;
            }
            if (sample == RemainingSpike.samples()) {
                if (!Files.exists(StateChecks.report("finish.txt"))) { return; }
                Files.writeString(StateChecks.report("remaining-client.txt"), REPORT);
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
                return;
            }
            String name = phase == 2 ? "used" : "equipment";
            var path = StateChecks.report("remaining-" + sample + "." + name);
            if (!Files.exists(java.nio.file.Path.of(path + ".ready"))) { return; }
            var state = JsonParser.parseString(Files.readString(java.nio.file.Path.of(path + ".json"))).getAsJsonObject();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            if (!ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("legs")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.LEGS))
                    || !ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("feet")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.FEET))) { return; }
            startY = state.get("startY").getAsDouble();
            if (phase == 0) {
                if (Math.abs(client.player.getY() - 81) > .01 || client.player.hasEffect(MobEffects.LEVITATION)) { return; }
                ack("equipment");
                phase = 1;
            } else if (phase == 1) {
                if (!client.player.hasEffect(MobEffects.LEVITATION) || !client.player.hasEffect(MobEffects.SLOW_FALLING)
                        || client.player.getY() < startY + 2 || client.player.getY() >= client.level.getMinY() + 16) { return; }
                require(client.player.getEffect(MobEffects.LEVITATION).getAmplifier() == 9 && client.player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3,
                        "Remote native potion parameters differ");
                REPORT.append("sample ").append(sample).append(": actual remote rescue rise=").append(client.player.getY() - startY)
                        .append(", native Levitation/Slow Falling amplifiers=9/3, complete root packets\n");
                ack("rescue");
                phase = 2;
            } else if (phase == 2) {
                ack("used");
                sample++;
                phase = 0;
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void ack(String name) throws Exception {
        Files.writeString(StateChecks.report("remaining-" + sample + "." + name + ".ok"), "Actual native remote observation accepted\n");
    }
}
