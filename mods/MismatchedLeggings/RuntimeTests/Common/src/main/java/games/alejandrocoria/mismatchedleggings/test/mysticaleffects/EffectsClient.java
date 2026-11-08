package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;

import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class EffectsClient {
    private static final List<String> MODES = List.of("forward", "strafe", "sprint", "crouch", "air");
    private static final List<String> KINDS = List.of("empty", "native-I", "native-III", "primary", "inverse");
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private static CompletableFuture<?> job;
    private static int phase;
    private static int nextPhase;
    private static int age;
    private static int ticks;
    private static int healthScene = -1;
    private static int scene;
    private static boolean finished;
    private static double x;
    private static double z;

    private EffectsClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 5200, "Mystical effects timeout phase=" + phase + " scene=" + scene);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            if (phase == 8) {
                if (++age >= 5) { SkeletonChecks.passed("client"); finished = true; client.stop(); }
                return;
            }
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            if (job != null) {
                if (!job.isDone()) { return; }
                job.join(); job = null; phase = nextPhase; age = 0;
            }
            age++;
            if (phase == 0 && age >= 20) {
                client.setScreen(null);
                client.player.input = new KeyboardInput(client.options);
                queue(client, () -> {
                    EffectsChecks.initialize(owner(client));
                    if (!EffectsChecks.absent()) { EffectsChecks.health(owner(client), -1); }
                }, EffectsChecks.absent() ? 7 : 1);
            } else if (phase == 1 && age >= 25) {
                int current = healthScene++;
                if (healthScene == 4 && System.getProperty("leggings.test.maGraph").equals("alone")
                        && (EffectsChecks.variant("normal") || EffectsChecks.variant("compatible"))) { healthScene = 8; }
                if (healthScene == 20 && System.getProperty("leggings.test.maGraph").equals("alone")) { healthScene = 22; }
                int following = healthScene;
                require(client.player.isAlive() && client.player.getHealth() <= client.player.getMaxHealth(), "Client health death/overflow scene=" + current + " health=" + client.player.getHealth() + " max=" + client.player.getMaxHealth());
                require(Math.abs(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) - EffectsChecks.expectedStep(current)) < .001, "Step sync/crouch mismatch");
                require(Math.abs(client.player.getMaxHealth() - EffectsChecks.expectedHealth(current)) < .001, "Health attribute packet/client cache mismatch");
                client.options.keyShift.setDown(following == 14);
                int scenes = EffectsChecks.variant("normal") || EffectsChecks.variant("compatible") ? 23 : 4;
                boolean focused = System.getProperty("leggings.test.maFocus", "full").equals("lifecycle");
                queue(client, () -> {
                    EffectsChecks.verifyHealth(owner(client), current);
                    if (following < scenes) { EffectsChecks.health(owner(client), following); }
                    else if (!focused) { prepare(owner(client), 0); }
                }, following < scenes ? 1 : focused ? 7 : 2);
            } else if (phase == 2 && age >= 20) {
                require(client.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() == false, "Equipment packet did not arrive");
                if (!mode(scene).equals("air") && !client.player.onGround()) {
                    require(age < 100, "Grounded control was not grounded: " + client.player.position());
                    return;
                }
                x = client.player.getX(); z = client.player.getZ();
                phase = 3; age = 0;
                keys(client, true);
            } else if (phase == 3 && age >= 30) {
                keys(client, false);
                double distance = Math.hypot(client.player.getX() - x, client.player.getZ() - z);
                int current = scene++;
                int following = scene;
                queue(client, () -> {
                    var player = owner(client);
                    SAMPLES.add(new Sample(mode(current), kind(current), distance,
                            Math.hypot(player.getX() - .5, player.getZ() - .5)));
                    EffectsChecks.write("samples.txt", SAMPLES.toString());
                    if (following < MODES.size() * KINDS.size()) { prepare(player, following); }
                }, following < MODES.size() * KINDS.size() ? 2 : 7);
            } else if (phase == 7) {
                keys(client, false);
                finish();
                client.disconnectWithSavingScreen();
                phase = 8; age = 0;
            }
        } catch (Throwable failure) {
            keys(client, false);
            SkeletonChecks.failed("client", failure);
            finished = true;
            client.stop();
        }
    }

    private static void prepare(ServerPlayer player, int scene) {
        // Teleport/equipment are server-owned; input axes are never assigned here.
        // The normal client's KeyboardInput and movement packets must produce the result.
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, switch (kind(scene)) {
            case "empty" -> NativeAugments.item("awakened_supremium_leggings");
            case "native-I" -> NativeAugments.item("awakened_supremium_leggings", "SPEED_I");
            case "native-III" -> NativeAugments.item("awakened_supremium_leggings", "SPEED_III");
            case "primary" -> NativeAugments.mixed("SPEED_III", false);
            default -> NativeAugments.mixed("SPEED_III", true);
        });
        player.getFoodData().setFoodLevel(20);
        player.setSprinting(false);
        player.setNoGravity(mode(scene).equals("air"));
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(.5, player.level().getMinY() + (mode(scene).equals("air") ? 8 : 1), .5, 0, 0);
        player.fallDistance = 0;
    }

    private static void keys(Minecraft client, boolean down) {
        String mode = mode(Math.min(scene, MODES.size() * KINDS.size() - 1));
        client.options.keyUp.setDown(down && !mode.equals("strafe"));
        client.options.keyLeft.setDown(down && mode.equals("strafe"));
        client.options.keySprint.setDown(down && mode.equals("sprint"));
        client.options.keyShift.setDown(down && mode.equals("crouch"));
    }

    private static String mode(int index) { return MODES.get(index / KINDS.size()); }
    private static String kind(int index) { return KINDS.get(index % KINDS.size()); }

    private static ServerPlayer owner(Minecraft client) {
        return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
    }

    private interface Work { void run() throws Exception; }

    private static void queue(Minecraft client, Work work, int next) {
        nextPhase = next;
        job = client.getSingleplayerServer().submit(() -> {
            try { work.run(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private static void finish() throws Exception {
        var report = new StringBuilder(EffectsChecks.REPORT);
        if (!EffectsChecks.absent()) {
            report.append(EffectsChecks.variant("normal") || EffectsChecks.variant("compatible")
                    ? "Health/step native, both mixed orientations, crouch, replacement, equal native-to-mixed swap, Health V actual break and removal checked with connected player\n"
                    : "Independent refused route with native Health/step positive, both mixed orientations and removal control passed\n");
        }
        if (SAMPLES.isEmpty()) { report.append("No movement matrix claimed in this focused/absent case\n"); }
        if (EffectsChecks.absent()) { report.append("Native absent: ordinary composition passed, no native class/callback claim\n"); }
        for (String mode : MODES) {
            var samples = SAMPLES.stream().filter(sample -> sample.mode.equals(mode)).toList();
            if (samples.isEmpty()) { continue; }
            double baseline = samples.getFirst().client;
            double nativeHigh = samples.get(2).client;
            require(baseline > .1, "Normal keyboard control did not move: " + mode);
            for (var sample : samples) {
                require(Math.abs(sample.server - sample.client) < Math.max(1, sample.client * .2), "Client/server movement diverged: " + sample);
                if (sample.kind.equals("primary") || sample.kind.equals("inverse")) {
                    require(Math.abs(sample.client - nativeHigh) < Math.max(.25, nativeHigh * .12), "Mixed/native speed differ: " + sample);
                }
                report.append(sample).append('\n');
            }
            boolean positive = nativeHigh > baseline * 1.05;
            boolean fabric = System.getProperty("leggings.test.maLoader").equals("Fabric");
            require(positive == (!fabric && !mode.equals("air")), "Native Speed positive/air/port control changed: " + mode);
            report.append(mode).append(" native Speed positive=").append(positive).append('\n');
        }
        EffectsChecks.write("movement.txt", report.toString());
    }

    private record Sample(String mode, String kind, double client, double server) {}
}
