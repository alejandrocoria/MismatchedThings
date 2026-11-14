package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real integrated-server entity updates and ordinary particle/item packets, not screenshots. */
public final class JustDireLavaClient {
    public static int particles;
    private static int ticks;
    private static int selected;
    private static int entityId;
    private static int samples;
    private static int backwards;
    private static double previousY = Double.NaN;
    private static double highest;
    private static boolean running;
    private static boolean finished;
    private static Future<?> job;
    private static final StringBuilder evidence = new StringBuilder();

    private JustDireLavaClient() {
    }

    public static void register(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent setup) ->
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance())));
    }

    private static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 2400, "Production Lava client timeout at case " + selected);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                            && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            if (client.player == null || client.level == null || client.getOverlay() != null) {
                return;
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.get();
                job = null;
            }
            if (!running) {
                if (selected == (Boolean.getBoolean("leggings.test.jdtBoots") ? 7 : 6)) {
                    Files.writeString(JustDireLavaChecks.report("client.txt"), evidence);
                    SkeletonChecks.passed("client");
                    finished = true;
                    client.stop();
                    return;
                }
                start(client);
                return;
            }
            if (!(client.level.getEntity(entityId) instanceof ItemEntity item)) {
                return;
            }
            highest = Math.max(highest, item.getY());
            boolean mixed = selected > 0;
            if (mixed && item.getAge() >= 10 && item.getAge() <= 50 && item.getY() < 82.7) {
                if (!Double.isNaN(previousY) && item.getY() < previousY - .02) {
                    backwards++;
                }
                previousY = item.getY();
                samples++;
            }
            if (!client.level.getBlockState(JustDireLavaChecks.SOURCE).is(Blocks.OBSIDIAN) || item.getItem().isDamaged()) {
                return;
            }
            require(highest > 80.8 && particles > 0, "No real synchronized rise/native particles at " + selected);
            if (mixed) {
                require(samples >= 10 && backwards == 0, "Client prediction fell during ascent: samples=" + samples + ", reversals=" + backwards);
            }
            evidence.append("case=").append(selected).append(" nativeParticles=").append(particles)
                    .append(" samples=").append(samples).append(" reversals=").append(backwards)
                    .append(" highest=").append(highest).append(" repaired synchronized root\n");
            var server = client.getSingleplayerServer();
            int id = entityId;
            boolean nativeOriginal = selected == 0;
            job = server.submit(() -> {
                var entity = (ItemEntity) server.overworld().getEntity(id);
                require(entity != null && !entity.getItem().isDamaged(), "Server root not repaired");
                require(JustDireLavaObservations.sounds == 1, "Missing/duplicate successful completion sound");
                try {
                    JustDireLavaObservations.validate(JustDireLavaChecks.SOURCE, nativeOriginal);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Native emission metadata changed", failure);
                }
                entity.discard();
            });
            selected++;
            running = false;
        } catch (Throwable failure) {
            SkeletonChecks.failed("client", failure);
            finished = true;
            client.stop();
        }
    }

    private static void start(Minecraft client) {
        particles = 0;
        samples = 0;
        backwards = 0;
        previousY = Double.NaN;
        highest = 80.2;
        running = true;
        var server = client.getSingleplayerServer();
        var uuid = client.player.getUUID();
        job = server.submit(() -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(6.5, 83, 10.5);
            ItemStack root;
            if (selected == 6) {
                try {
                    root = JustDireLavaChecks.boots();
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot build current sibling control", failure);
                }
            } else if (selected == 5) {
                root = JustDireLavaChecks.diamond(true);
            } else {
                root = selected == 0 ? JustDireLavaChecks.nativeItem("blazegold_leggings") : JustDireLavaChecks.mixed(selected == 2, selected == 4);
            }
            if (selected != 3) {
                root.setDamageValue(30);
            }
            var entity = JustDireLavaChecks.spawn(level, root, JustDireLavaChecks.SOURCE);
            require(level.addFreshEntity(entity), "Cannot spawn live Lava entity");
            entityId = entity.getId();
        });
    }
}
