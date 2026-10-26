package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.clear;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.close;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Bounded connected smoke: ordinary equipment/attribute packets and native keyboard double jumps. */
final class AetherIIClientTest {
    private static final boolean REJOIN = Boolean.getBoolean("mismatchedboots.runtimetest.aetherIIRejoin");
    private static final String OUTPUT = "aether-ii-" + (REJOIN ? "rejoin" : "client");
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks;
    private static int scene;
    private static int phase;
    private static int age;
    private static int settled;
    private static boolean finished;
    private static boolean boosted;
    private static boolean backupConfirmed;
    private static double peak;
    private static double nativePeak;
    private static Future<String> job;
    private static JsonObject expected;
    private static List<ItemStack> roots; // Integrated-server thread only; JSON expectations cross threads.
    private static ItemStack worn; // Integrated-server identity witness only.
    private static ServerPlayer serverOwner; // Server tick observation, no movement writes.
    private static double serverPeak;
    private static int preparedTick;

    private AetherIIClientTest() {
    }

    static void observeServer() {
        if (serverOwner != null) {
            serverPeak = Math.max(serverPeak, serverOwner.getY() - 81);
        }
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }

        try {
            require(++ticks < 2400, "Aether client timeout: scene=" + scene + " phase=" + phase + " age=" + age
                    + " y=" + (client.player == null ? "none" : client.player.getY()) + " screen="
                    + (client.screen == null ? "none" : client.screen.getClass().getName()) + " state=" + expected);
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (backupConfirmed) {
                    return;
                }
                backupConfirmed = true;
                // Only this opt-in fixture's disposable copied world: use the native skip-backup/join button.
                REPORT.append("Disposable-world confirmation: ").append(backup.getTitle().getString()).append('\n');
                backup.children().stream().filter(child -> child instanceof Button button
                              && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                      .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                client.setScreen(null);
            }
            if (client.screen != null) {
                return;
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                expected = JsonParser.parseString(job.get()).getAsJsonObject();
                job = null;
                if (phase == 5) {
                    complete(client);
                    return;
                }
            }

            var server = client.getSingleplayerServer();
            var id = client.player.getUUID();
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Aether client not hidden/muted");
                client.player.input = new KeyboardInput(client.options);
                job = server.submit(() -> {
                    try {
                        var owner = server.getPlayerList().getPlayer(id);
                        if (REJOIN) {
                            var saved = AetherIILifecycleTest.read("aether-ii-player.json");
                            require(saved.get("process").getAsLong() != ProcessHandle.current().pid()
                                            && saved.get("uuid").getAsString().equals(id.toString()), "Rejoin reused process/changed identity");
                            roots = new ArrayList<>();
                            for (int i = 0; i < 6; i++) {
                                roots.add(owner.getInventory().getItem(9 + i));
                            }
                            AetherIILifecycleTest.compare(server, roots, saved.getAsJsonArray("roots"));
                            REPORT.append("Same player UUID in different JVM: six complete used player roots matched before callbacks\n");
                        } else {
                            roots = AetherIILifecycleTest.roots(server).stream().map(ItemStack::copy).toList();
                            AetherIILifecycleTest.compare(server, roots, AetherIILifecycleTest.read("aether-ii-world.json").getAsJsonArray("roots"));
                        }
                        AetherIILifecycleTest.check(owner, roots, REPORT);
                        owner.getInventory().clearContent();
                        owner.removeAllEffects();
                        owner.setGameMode(GameType.SURVIVAL);
                        owner.getFoodData().setFoodLevel(10);
                        owner.setNoGravity(false);
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) {
                                server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                            }
                        }

                        return prepare(owner);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
                phase = 1;
                return;
            }
            if (!received(client)) {
                return;
            }
            if (scene < 4) {
                // Two Health/Neptune inverses, each followed by native set deactivation.
                if (++settled < 10) {
                    return;
                }
                close(client.player.getAttributeValue(Attributes.MAX_HEALTH), 21, "Connected Health snapshot");
                boolean active = scene % 2 == 0;
                close(client.player.getAttributeValue(Attributes.OXYGEN_BONUS), active ? 1.5 : 0, "Connected native Neptune set");
                REPORT.append("PACKET Health/Neptune scene=")
                      .append(scene)
                      .append(" root+attributes received, native set active=")
                      .append(active)
                      .append('\n');
                next(client);
                return;
            }
            flight(client);
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.options.keyJump.setDown(false);
            client.stop();
        }
    }

    private static boolean received(Minecraft client) {
        if (expected == null || !expected.has("root")) {
            return false;
        }
        if (!expected.get("ready").getAsBoolean()) {
            var server = client.getSingleplayerServer();
            var id = client.player.getUUID();
            job = server.submit(() -> poll(server.getPlayerList().getPlayer(id)));
            return false;
        }

        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var root = ItemStack.CODEC.parse(ops, expected.get("root")).getOrThrow();
        if (!ItemStack.matches(root, client.player.getItemBySlot(EquipmentSlot.FEET))) {
            return false;
        }
        // Server effects / vanilla equipment updates are the oracle, not a client-side forced modifier.
        for (var entry : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.OXYGEN_BONUS, Attributes.FALL_DAMAGE_MULTIPLIER)) {
            String name = entry.unwrapKey().orElseThrow().identifier().toString();
            if (Math.abs(client.player.getAttributeValue(entry) - expected.getAsJsonObject("attributes").get(name).getAsDouble()) > 1E-6) {
                return false;
            }
        }

        return true;
    }

    private static void flight(Minecraft client) throws Exception {
        var player = client.player;
        boolean supported = scene != 7;
        if (phase == 1) {
            if (!player.onGround() || Math.abs(player.getY() - 81) > .001) {
                settled = 0;
                return;
            }
            if (++settled < 15) {
                return;
            }
            require(!jumpUsed(player), "Native grounded reset missing");
            client.options.keyJump.setDown(true);
            phase = 2;
            age = 0;
            peak = 0;
            boosted = false;
            settled = 0;
            return;
        }
        client.options.keyJump.setDown(false);
        age++;
        peak = Math.max(peak, player.getY() - 81);
        require(age < 110, "Flight did not land scene=" + scene + " peak=" + peak);
        // Initial press -> release -> second press after the ordinary jump delay. No velocity/attachment writes.
        if (age == 7 || age == 20) {
            client.options.keyJump.setDown(true);
        }
        if (!player.onGround() && jumpUsed(player)) {
            boosted = true;
        }
        if (age < 12 || !player.onGround()) {
            return;
        }
        if (++settled < 8) {
            return;
        }
        require(boosted == supported, "Wrong native double-jump admission scene=" + scene + " boosted=" + boosted);
        require(!jumpUsed(player), "Landing did not reset native jump attachment");
        if (scene == 4) {
            nativePeak = peak;
            require(peak > 2, "Native double-jump control did not exceed ordinary height");
        } else if (supported) {
            require(Math.abs(peak - nativePeak) < .06, "Mixed keyboard height differs: " + peak + " vs " + nativePeak);
        } else {
            require(peak < nativePeak - .5, "Missing-third-piece control double jumped");
        }
        REPORT.append("FLIGHT scene=").append(scene).append(" native attachment boost=").append(boosted)
              .append(" apex=").append(peak).append("; grounded reset, third press cannot exceed native height\n");
        next(client);
    }

    private static void next(Minecraft client) {
        client.options.keyJump.setDown(false);
        settled = 0;
        phase = 1;
        var server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        int completed = scene++;
        job = server.submit(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(id);
                require(owner.getItemBySlot(EquipmentSlot.FEET) == worn, "Native tick temporarily substituted equipment");
                var serialized = ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), worn).getOrThrow();
                require(serialized.equals(expected.get("root")), "Connected scene mutated full root/history");
                if (completed >= 4) {
                    require(owner.onGround() && Math.abs(owner.getY() - 81) < .1, "Client landing did not reach server");
                    require(Math.abs(serverPeak - peak) < .3, "Actual keyboard ascent did not reach server: " + serverPeak + " vs " + peak);
                }
                if (scene < 8) {
                    return prepare(owner);
                }
                clear(owner);
                for (int i = 0; i < 6; i++) {
                    owner.getInventory().setItem(9 + i, roots.get(i));
                }

                var manifest = new JsonObject();
                manifest.addProperty("process", ProcessHandle.current().pid());
                manifest.addProperty("uuid", id.toString());
                manifest.add("roots", AetherIILifecycleTest.encode(server, roots));
                if (!REJOIN) {
                    NetworkAcceptanceFiles.write("aether-ii-player.json", manifest.toString());
                }
                server.getPlayerList().saveAll();
                return "{}";
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        if (scene == 8) {
            phase = 5;
        }
    }

    private static String prepare(ServerPlayer owner) throws Exception {
        clear(owner);
        if (scene < 4) {
            owner.setItemSlot(EquipmentSlot.HEAD, item("neptune_helmet"));
            if (scene % 2 == 0) {
                owner.setItemSlot(EquipmentSlot.CHEST, item("neptune_chestplate"));
            }
            worn = roots.get(scene / 2);
        } else {
            owner.setItemSlot(EquipmentSlot.HEAD, item("gravitite_helmet"));
            owner.setItemSlot(EquipmentSlot.CHEST, item("gravitite_chestplate"));
            worn = scene == 4 ? item("gravitite_boots") : scene == 7 ? new ItemStack(Items.DIAMOND_BOOTS) : roots.get(scene - 1);
        }
        owner.setItemSlot(EquipmentSlot.FEET, worn);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.connection.teleport(.5, 81, .5, 0, 0);
        preparedTick = owner.tickCount;
        serverOwner = owner;
        serverPeak = 0;
        // poll() waits for ordinary native tick/equipment callbacks, never writes expected modifiers.
        return poll(owner);
    }

    private static String poll(ServerPlayer owner) {
        var value = new JsonObject();
        value.addProperty("ready", owner.tickCount - preparedTick >= 3);
        value.add("root", ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), worn).getOrThrow());
        var attributes = new JsonObject();
        for (var entry : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.OXYGEN_BONUS, Attributes.FALL_DAMAGE_MULTIPLIER)) {
            attributes.addProperty(entry.unwrapKey().orElseThrow().identifier().toString(), owner.getAttributeValue(entry));
        }
        value.add("attributes", attributes);
        return value.toString();
    }

    private static boolean jumpUsed(Player player) throws Exception {
        var type = Class.forName("com.aetherteam.aetherii.attachment.AetherIIDataAttachments");
        var supplier = (Supplier<?>) type.getField("ABILITY_BEHAVIOR").get(null);
        Object attachment = player.getClass().getMethod("getData", Supplier.class).invoke(player, supplier);
        return (boolean) attachment.getClass().getMethod("isGravititeJumpUsed").invoke(attachment);
    }

    private static void complete(Minecraft client) {
        NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n");
        finished = true;
        client.options.keyJump.setDown(false);
        client.stop();
    }
}
