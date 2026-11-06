package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Early prediction proof: real local connection and keyboard-driven movement, no forced client physics. */
final class AquacultureClientChecks {
    private static final String[] SCENES = { "ordinary", "primary", "crouch", "release-crouch", "jump", "release-jump", "unequip", "inverse" };
    private static final StringBuilder REPORT = new StringBuilder();
    private static CompletableFuture<Snapshot> job;
    private static Snapshot snapshot;
    private static int ticks;
    private static int scene;
    private static int age;
    private static int samples;
    private static int startServerTick;
    private static double initialY;
    private static double initialServerY;
    private static boolean prepared;
    private static boolean backupConfirmed;
    private static boolean finished;

    private AquacultureClientChecks() {
    }

    @SuppressWarnings("deprecation")
    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 2400, "Aquaculture client timed out: " + scene + "/" + age + "/" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                            && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0,
                    "Aquaculture automated client must remain hidden and muted");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (!backupConfirmed) {
                    backupConfirmed = true;
                    backup.children().stream().filter(child -> child instanceof Button button
                                    && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                            .map(Button.class::cast).findFirst().orElseThrow()
                            .onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                }
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null
                    || client.screen != null || client.getSingleplayerServer() == null) {
                return;
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                snapshot = job.join();
                job = null;
            }
            if (!prepared) {
                client.player.input = new KeyboardInput(client.options);
                keys(client, false, false);
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                int nextScene = scene;
                job = server.submit(() -> prepare(server.getPlayerList().getPlayer(id), nextScene));
                prepared = true;
                snapshot = null;
                return;
            }
            if (snapshot == null || !ItemStack.matches(snapshot.legs(), client.player.getItemBySlot(EquipmentSlot.LEGS))
                    || !client.player.isEyeInFluid(FluidTags.WATER)) {
                return;
            }

            age++;
            // Let teleport/equipment packets and the real fluid/input caches settle before sampling.
            if (age <= 15) {
                poll(client);
                return;
            }
            keys(client, scene == 2, scene == 4);
            if (age == 16) {
                initialY = client.player.getY();
                initialServerY = snapshot.y();
                startServerTick = snapshot.tick();
            }
            boolean buoyant = scene == 1 || scene == 3 || scene == 5 || scene == 7;
            boolean released = scene == 0 || scene == 6;
            if (age > 22) {
                require(client.player.isEyeInFluid(FluidTags.WATER) && snapshot.eyesInWater(), "Scene left the water tank");
                require(!client.player.isNoGravity() && !snapshot.noGravity(), "Mixed client session acquired a gravity flag");
                if (buoyant || released) {
                    require((client.player.getGravity() == 0) == buoyant && (snapshot.gravity() == 0) == buoyant,
                            "Client/server query disagreement in " + SCENES[scene]);
                }
                require(Math.abs(client.player.getY() - snapshot.y()) < .6, "Client/server position divergence in " + SCENES[scene]);
                samples++;
            }
            if (age < 90) {
                poll(client);
                return;
            }

            double delta = client.player.getY() - initialY;
            double serverDelta = snapshot.y() - initialServerY;
            require(samples >= 50 && snapshot.tick() - startServerTick >= 40, "Movement scene did not advance a real server");
            if (buoyant) {
                require(Math.abs(delta) < .15 && Math.abs(serverDelta) < .15, "Buoyant scene drifted: " + delta + "/" + serverDelta);
            } else if (scene == 4) {
                require(delta > 1 && serverDelta > 1, "Keyboard jump did not release buoyancy/upward motion");
            } else {
                require(delta < -1 && serverDelta < -1, "Ordinary/crouch/unequip scene did not sink");
            }
            REPORT.append(SCENES[scene]).append(": samples=").append(samples)
                    .append("; server ticks=").append(snapshot.tick() - startServerTick)
                    .append("; client/server deltaY=").append(delta).append('/').append(serverDelta).append('\n');
            keys(client, false, false);
            scene++;
            if (scene == SCENES.length) {
                Files.writeString(Path.of(System.getProperty("leggings.test.report"), "aquaculture-client.txt"), REPORT.toString());
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
            } else {
                prepared = false;
                age = 0;
                samples = 0;
            }
        } catch (Throwable failure) {
            finished = true;
            keys(client, false, false);
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static Snapshot prepare(ServerPlayer player, int scene) {
        var level = player.level();
        if (scene == 0) {
            level.setChunkForced(0, 0, true);
            for (int x = 0; x < 6; x++) {
                for (int y = 65; y < 96; y++) {
                    for (int z = 0; z < 6; z++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 3);
                    }
                }
            }
        }
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.setAirSupply(20_000);
        player.setNoGravity(false);
        var source = AquacultureChecks.nativeItem("neptunium_leggings");
        ItemStack legs = scene == 0 ? new ItemStack(Items.IRON_LEGGINGS)
                : scene == 6 ? ItemStack.EMPTY
                : AquacultureChecks.pair(source, new ItemStack(Items.IRON_LEGGINGS)).get(scene == 7 ? 1 : 0);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        // Its real inventory tick keeps clearing native NoGravity. Our query must remain independent.
        player.getInventory().setItem(10, AquacultureChecks.nativeItem("neptunium_boots"));
        player.inventoryMenu.broadcastChanges();
        player.connection.teleport(2.5, 80, 2.5, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        return snapshot(player);
    }

    private static void poll(Minecraft client) {
        var server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        job = server.submit(() -> snapshot(server.getPlayerList().getPlayer(id)));
    }

    @SuppressWarnings("deprecation")
    private static Snapshot snapshot(ServerPlayer player) {
        return new Snapshot(player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getY(), player.getGravity(),
                player.isNoGravity(), player.isEyeInFluid(FluidTags.WATER), player.level().getServer().getTickCount());
    }

    private static void keys(Minecraft client, boolean crouch, boolean jump) {
        client.options.keyShift.setDown(crouch);
        client.options.keyJump.setDown(jump);
    }

    private record Snapshot(ItemStack legs, double y, double gravity, boolean noGravity, boolean eyesInWater, int tick) { }
}
