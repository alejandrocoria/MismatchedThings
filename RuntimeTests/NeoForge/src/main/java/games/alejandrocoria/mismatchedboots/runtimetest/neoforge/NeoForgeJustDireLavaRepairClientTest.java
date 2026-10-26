package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

/** Actual server-spawned entity, vanilla particle packets and JDT client factory, never manual particle construction. */
public final class NeoForgeJustDireLavaRepairClientTest {
    private static final BlockPos SOURCE = new BlockPos(5, 80, 5);
    private static int ticks;
    private static int phase;
    private static int wait;
    private static int id;
    private static int nativeParticles;
    private static int mixedParticles;
    private static int nativeHealthyParticles;
    private static int mixedHealthyParticles;
    private static int predictionChecks;
    private static boolean finished;
    private static double highest;
    private static Future<?> job;
    private static final ConcurrentLinkedQueue<Motion> motion = new ConcurrentLinkedQueue<>();
    private static volatile boolean tracingMixed;
    private static volatile boolean tracingHealthy;

    private record Motion(boolean mixed, boolean healthy, boolean client, int age, double y, double vy, boolean onGround) {
    }

    private NeoForgeJustDireLavaRepairClientTest() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }

        try {
            if (++ticks > 3_600) {
                throw new IllegalStateException("Lava client timeout, phase=" + phase + " particles=" + JustDireLavaRepairTestSupport.clientParticles);
            }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            if (job != null && !job.isDone()) {
                return;
            }
            if (job != null) {
                job.get();
                job = null;
            }
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Client not hidden/muted");
                spawn(client, false, false);
                phase = 1;
            } else if (phase == 1 || phase == 3 || phase == 5 || phase == 7) {
                if (!(client.level.getEntity(id) instanceof ItemEntity item)) {
                    return;
                }

                boolean mixed = phase == 3 || phase == 7;
                boolean healthy = phase >= 5;
                if (client.level.getBlockState(SOURCE).is(Blocks.LAVA)) {
                    motion.add(new Motion(mixed, healthy, true, item.getAge(), item.getY(), item.getDeltaMovement().y, item.onGround()));
                    int bit = healthy ? 2 : 1;
                    if (mixed && (predictionChecks & bit) == 0 && item.getAge() >= 20 && item.getY() > 81) {
                        predictionBoundaries(client, item);
                        predictionChecks |= bit;
                    }
                }
                highest = Math.max(highest, item.getY());
                if (!client.level.getBlockState(SOURCE).is(Blocks.OBSIDIAN)) {
                    return;
                }
                if (mixed && item.getItem().isDamaged()) {
                    return;
                }

                String observation = "phase=" + phase + " highest=" + highest + " particles="
                        + JustDireLavaRepairTestSupport.clientParticles + " age=" + item.getAge()
                        + " chunkLoaded=" + client.level.hasChunkAt(SOURCE)
                        + " screen=" + (client.screen == null ? "none" : client.screen.getClass().getSimpleName());
                require(highest > 80.5, "No synchronized item rise; " + observation);
                require(JustDireLavaRepairTestSupport.clientParticles > 0, "No native factory particles; " + observation);
                boolean mixedCompleted = mixed;
                var server = client.getSingleplayerServer();
                job = server.submit(() -> {
                    var entity = (ItemEntity) server.overworld().getEntity(id);
                    require(entity != null && !entity.getItem().isDamaged() && server.overworld().getBlockState(SOURCE).is(Blocks.OBSIDIAN),
                            "Server did not complete actual spawned item/source consumption");
                    if (mixedCompleted) {
                        require(JustDireLavaRepairTestSupport.sounds.equals(java.util.List.of(SOURCE)), "Completion sound not emitted exactly once");
                        try {
                            JustDireLavaRepairTestSupport.validateEmissions(SOURCE);
                        } catch (ReflectiveOperationException failure) {
                            throw new AssertionError(failure);
                        }
                    }
                    entity.discard();
                    return null;
                });
                if (phase == 1) {
                    nativeParticles = JustDireLavaRepairTestSupport.clientParticles;
                } else if (phase == 3) {
                    mixedParticles = JustDireLavaRepairTestSupport.clientParticles;
                } else if (phase == 5) {
                    nativeHealthyParticles = JustDireLavaRepairTestSupport.clientParticles;
                } else {
                    mixedHealthyParticles = JustDireLavaRepairTestSupport.clientParticles;
                }
                phase++;
                wait = 0;
            } else if (phase == 2 || phase == 4 || phase == 6) {
                if (++wait < 80) {
                    return;
                }
                spawn(client, phase != 4, phase >= 4);
                phase++;
            } else {
                require(predictionChecks == 3, "Missing damaged/healthy prediction boundary checks");
                NetworkAcceptanceFiles.write("just-dire-lava-repair-client-report.txt",
                                             "Native/mixed actual spawned ItemEntities; synchronized rise/obsidian; mixed repaired stack packet; vanilla particle codec/transport; native JDT client factory: native="
                                                     + nativeParticles + ", mixed=" + mixedParticles + ", nativeHealthy=" + nativeHealthyParticles + ", mixedHealthy=" + mixedHealthyParticles
                                                     + "; successful completion sound/source/visual metadata; client prediction recovery/cancellation/timeout/stale-position/non-authority controls\n" + motionReport());
                NetworkAcceptanceFiles.write("just-dire-lava-repair-client-success.txt", "neoforge\n");
                finished = true;
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-lava-repair-client", failure);
            finished = true;
        }
        if (finished) {
            client.stop();
        }
    }

    private static void spawn(Minecraft client, boolean mixed, boolean healthy) {
        var server = client.getSingleplayerServer();
        var uuid = client.player.getUUID();
        highest = 80.2;
        JustDireLavaRepairTestSupport.clientParticles = 0;
        job = server.submit(() -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.SPECTATOR);
            player.getInventory().clearContent();
            player.teleportTo(6.5, 83, 10.5);
            player.setYRot(180);
            player.setXRot(20);
            for (BlockPos pos : BlockPos.betweenClosed(2, 79, 2, 10, 88, 12)) {
                level.setBlock(pos, (pos.getY() == 79 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            level.setBlock(SOURCE, Blocks.LAVA.defaultBlockState(), 3);
            JustDireLavaRepairTestSupport.reset();
            ItemStack source = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "blazegold_boots")));
            ItemStack stack = mixed ? MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary() : source;
            if (!healthy) {
                stack.setDamageValue(100);
            }

            ItemEntity item = new ItemEntity(level, 5.5, 80.2, 5.5, stack);
            item.setDeltaMovement(Vec3.ZERO);
            require(level.addFreshEntity(item), "Could not spawn item");
            id = item.getId();
            tracingMixed = mixed;
            tracingHealthy = healthy;
        });
    }

    static void observeServer(MinecraftServer server) {
        if (server.overworld().getEntity(id) instanceof ItemEntity item && server.overworld().getBlockState(SOURCE).is(Blocks.LAVA)) {
            motion.add(new Motion(tracingMixed, tracingHealthy, false, item.getAge(), item.getY(), item.getDeltaMovement().y, item.onGround()));
        }
    }

    private static String motionReport() {
        StringBuilder summary = new StringBuilder("Early ascent trajectory (ages 10..50, below hover height; before source completion):\n");
        StringBuilder csv = new StringBuilder("mixed,healthy,client,age,y,vy,onGround\n");
        for (Motion sample : motion) {
            csv.append(String.format(Locale.ROOT,
                                     "%s,%s,%s,%d,%.8f,%.8f,%s\n",
                                     sample.mixed,
                                     sample.healthy,
                                     sample.client,
                                     sample.age,
                                     sample.y,
                                     sample.vy,
                                     sample.onGround));
        }
        NetworkAcceptanceFiles.write("just-dire-lava-repair-trajectory.csv", csv.toString());
        boolean smooth = true;
        for (boolean healthy : List.of(false, true)) {
            for (boolean mixed : List.of(false, true)) {
                for (boolean clientSide : List.of(false, true)) {
                    List<Motion> samples = new ArrayList<>();
                    for (Motion sample : motion) {
                        if (sample.mixed == mixed && sample.healthy == healthy && sample.client == clientSide
                                && sample.age >= 10 && sample.age <= 50 && sample.y < SOURCE.getY() + 2.8) {
                            samples.add(sample);
                        }
                    }

                    int drops = 0;
                    int gravityTicks = 0;
                    double largestDrop = 0;
                    for (int i = 0; i < samples.size(); i++) {
                        if (samples.get(i).vy < -.015) {
                            gravityTicks++;
                        }
                        if (i > 0 && samples.get(i).age > samples.get(i - 1).age) {
                            double delta = samples.get(i).y - samples.get(i - 1).y;
                            if (delta < -.015) {
                                drops++;
                                largestDrop = Math.max(largestDrop, -delta);
                            }
                        }
                    }
                    summary.append(String.format(Locale.ROOT,
                                                 "%s %s %s: samples=%d downSteps=%d maxDrop=%.6f negativeVy=%d\n",
                                                 healthy ? "healthy" : "damaged",
                                                 mixed ? "mixed" : "native",
                                                 clientSide ? "client" : "server",
                                                 samples.size(),
                                                 drops,
                                                 largestDrop,
                                                 gravityTicks));
                    smooth &= samples.size() >= 24 && drops == 0 && gravityTicks == 0;
                }
            }
        }
        NetworkAcceptanceFiles.write("just-dire-lava-repair-trajectory-summary.txt", summary.toString());
        require(smooth, "Native/mixed healthy/damaged ascent regression: " + summary);
        return summary.toString();
    }

    private static void predictionBoundaries(Minecraft client, ItemEntity tracked) {
        ItemStack root = tracked.getItem().copy();
        ItemStack before = root.copy();
        ItemEntity shadow = new ItemEntity(client.level, 5.5, 81.5, 5.5, root);
        shadow.setDeltaMovement(0, tracked.getDeltaMovement().y, 0);
        double initial = shadow.getY();
        JustDireLavaRepairCompatibility.tick(root, shadow);
        require(shadow.getY() > initial, "Already-airborne prediction did not recover from synchronized rise velocity");
        for (int i = 0; i < 210; i++) {
            JustDireLavaRepairCompatibility.tick(root, shadow);
        }

        double timedOut = shadow.getY();
        JustDireLavaRepairCompatibility.tick(root, shadow);
        require(shadow.getY() == timedOut && client.level.getBlockState(SOURCE).is(Blocks.LAVA)
                        && ItemStack.matches(root, before), "Client predicted a world/repair commit or ignored timeout");
        ItemEntity cancelled = new ItemEntity(client.level, 5.5, 80.2, 5.5, root.copy());
        JustDireLavaRepairCompatibility.tick(cancelled.getItem(), cancelled);
        cancelled.getItem().set(JustDireLavaRepairTestSupport.<Boolean>component("lavarepair_toggle"), false);
        double y = cancelled.getY();
        JustDireLavaRepairCompatibility.tick(cancelled.getItem(), cancelled);
        require(cancelled.getY() == y && !cancelled.onGround(), "Disabled client prediction kept moving");
        ItemEntity stale = new ItemEntity(client.level, 10.5, 84, 5.5, root.copy());
        stale.setDeltaMovement(Vec3.ZERO);
        stale.getItem().set(JustDireLavaRepairTestSupport.<Integer>component("floatingticks"), 79);
        stale.getItem().set(JustDireLavaRepairTestSupport.<BlockPos>component("lavapos"), SOURCE);
        JustDireLavaRepairCompatibility.tick(stale.getItem(), stale);
        require(stale.getY() == 84 && client.level.getBlockState(SOURCE).is(Blocks.LAVA), "Stale coordinates authorized client motion/consumption");
        ItemEntity ordinary = new ItemEntity(client.level, 5.5, 81.5, 5.5, root.copy());
        ordinary.setDeltaMovement(0, .3, 0);
        JustDireLavaRepairCompatibility.tick(ordinary.getItem(), ordinary);
        require(ordinary.getY() == 81.5, "Arbitrary airborne velocity started a predicted ritual");
        // The live tracked entity must also recover on its next tick, not only a fixture-made shadow.
        JustDireLavaRepairCompatibility.clearClientEpisodes();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
