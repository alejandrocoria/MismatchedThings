package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Passive TCP observer: never manufactures player motion, damage, ability calls or sound packets. */
public final class DefenseLifecycleClient {
    private static int ticks;
    private static int sample;
    private static int phase;
    private static int sounds;
    private static int engineSounds;
    private static int nativeSounds;
    private static int nativeEngineSounds;
    private static int released;
    private static boolean ready;
    private static boolean finished;
    private static double minimumY;
    private static double rise;
    private static double nativeRise;
    private static int landed;
    private static final StringBuilder REPORT = new StringBuilder();

    private DefenseLifecycleClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4800, "Connected defense client timeout: " + sample + "/" + phase + " screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Defense client must stay hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "Connected defensive server failed");
            if (client.level == null || client.player == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Defensive acceptance requires real dedicated TCP");
            if (!ready) {
                listen();
                Files.writeString(StateChecks.report("defense-client-ready.ok"), "Dedicated client loaded\n");
                ready = true;
            }
            if (sample == DefenseLifecycle.SAMPLES) {
                if (!Files.exists(StateChecks.report("finish.txt"))) { return; }
                Files.writeString(StateChecks.report("defense-client.txt"), REPORT);
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
                return;
            }
            if (phase <= 3) {
                var state = received(client, "health-" + phase);
                if (state == null) { return; }
                if (phase == 3) {
                    var attacker = client.level.getEntity(state.get("attacker").getAsInt());
                    if (attacker == null || attacker.getX() < 10.65) { return; }
                }
                acknowledge("health-" + phase++);
            } else if (phase == 4) {
                if (received(client, "fall") == null || Math.abs(client.player.getY() - 95) > .01) { return; }
                sounds = engineSounds = 0;
                minimumY = Double.MAX_VALUE;
                rise = 0;
                landed = 0;
                acknowledge("fall");
                released = ticks;
                phase = 5;
            } else if (phase == 5) {
                minimumY = Math.min(minimumY, client.player.getY());
                if (minimumY <= 81.05 && client.player.getY() > 81.05) { rise = Math.max(rise, client.player.getY() - 81); }
                require(ticks - released < 300, "Client rebound timeout sample=" + sample + " minY=" + minimumY + " y=" + client.player.getY()
                        + " rise=" + rise + " sounds=" + sounds + " engine=" + engineSounds);
                if (minimumY <= 81.05 && landed == 0) { landed = ticks; }
                // SG/Lib's pinned dedicated client never drains its server-tick rebound queue.
                // Observe a real landing window and require native parity; do not synthesize the missing push.
                if (landed == 0 || ticks - landed < 20 || sounds == 0 || engineSounds == 0) { return; }
                if (sample == 0) {
                    nativeSounds = sounds;
                    nativeEngineSounds = engineSounds;
                    nativeRise = rise;
                    require(nativeRise < .01 && nativeSounds == 2, "Pinned native dedicated motion/sound control changed");
                }
                require(Math.abs(rise - nativeRise) < .01 && Math.abs(client.player.getY() - 81) < .01, "Native/mixed dedicated-client motion differs");
                require(sounds == nativeSounds && engineSounds == nativeEngineSounds && engineSounds == 1, "Native/mixed connected sound count differs");
                acknowledge("landing");
                phase = 6;
            } else if (phase == 6 && received(client, "used") != null) {
                REPORT.append("sample ").append(sample).append(": actual health/equipment packets, moving melee attacker, dedicated-client rise=")
                        .append(rise).append(", native sound requests=").append(sounds).append(" engine=").append(engineSounds)
                        .append(", native limitation preserved, full used roots received\n");
                acknowledge("used");
                sample++;
                phase = 0;
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static JsonObject received(Minecraft client, String name) throws Exception {
        var path = StateChecks.report("defense-" + sample + "." + name + ".json");
        if (!Files.exists(StateChecks.report("defense-" + sample + "." + name + ".ready"))) { return null; }
        var state = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if (!ItemStack.matches(ItemStack.CODEC.parse(ops, state.get("legs")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.LEGS))
                || !ItemStack.matches(ItemStack.OPTIONAL_CODEC.parse(ops, state.get("feet")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.FEET))
                || Math.abs(client.player.getHealth() - state.get("health").getAsFloat()) > 1E-4) { return null; }
        return state;
    }

    private static void acknowledge(String name) throws Exception {
        Files.writeString(StateChecks.report("defense-" + sample + "." + name + ".ok"), "Actual client observation accepted\n");
    }

    private static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getLevel().isClientSide() && event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Native client sound parameters changed");
                sounds++;
            }
        });
        NeoForge.EVENT_BUS.addListener((PlaySoundEvent event) -> {
            if (event.getOriginalSound().getIdentifier().equals(SoundEvents.SLIME_BLOCK_FALL.location())) { engineSounds++; }
        });
    }
}
