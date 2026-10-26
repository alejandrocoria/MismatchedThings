package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Extension of the existing connected driver. Only JSON crosses threads; no manual magnet/queue/motion/pickup calls. */
final class SilentGearMagnetClientTest {
    private static final List<Sample> SAMPLES = List.of(
            new Sample(false, false, EquipmentSlot.FEET), new Sample(true, false, EquipmentSlot.FEET),
            new Sample(true, false, EquipmentSlot.MAINHAND), new Sample(true, false, EquipmentSlot.OFFHAND),
            new Sample(false, true, EquipmentSlot.FEET), new Sample(true, true, EquipmentSlot.FEET),
            new Sample(true, true, EquipmentSlot.OFFHAND));
    private static Future<String> job;
    private static JsonObject state;
    private static int index;
    private static boolean clientMoved;
    private static boolean completing;
    private static final StringBuilder report = new StringBuilder();
    // Server-thread-only working state, never dereferenced by the client tick.
    private static Session session;
    private static ItemStack persisted;

    private SilentGearMagnetClientTest() {
    }

    static boolean advance(Minecraft client, boolean rejoin) throws Exception {
        if (completing) {
            if (!job.isDone()) {
                return false;
            }
            job.get();
            job = null;
            return true;
        }
        if (job != null) {
            if (!job.isDone()) {
                return false;
            }
            state = JsonParser.parseString(job.get()).getAsJsonObject();
            job = null;
        }

        var server = client.getSingleplayerServer();
        var uuid = client.player.getUUID();
        if (state == null) {
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(uuid);
                try {
                    if (persisted == null) {
                        persisted = rejoin ? owner.getInventory().getItem(11) : SilentGearLifecycleTest.barrel(server).getItem(8).copy();
                        if (rejoin) {
                            var saved = JsonParser.parseString(Files.readString(manifest())).getAsJsonObject();
                            require(saved.get("process").getAsLong() != ProcessHandle.current().pid()
                                            && saved.get("uuid").getAsString().equals(uuid.toString()), "Magnet rejoin reused process/changed UUID");
                            var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                            // Compare decoded components, not JSON number spelling across native float/NBT encoding.
                            ItemStack expected = ItemStack.CODEC.parse(ops, saved.get("root")).getOrThrow();
                            require(ItemStack.matches(expected, persisted), "Player save/rejoin changed prepared magnet root/sources");
                        }
                    }

                    Sample sample = rejoin ? SAMPLES.get(1) : SAMPLES.get(index);
                    session = new Session(owner, sample, index == 6);
                    return session.poll();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            return false;
        }

        int stage = state.get("stage").getAsInt();
        var moving = client.level.getEntity(state.get("moving").getAsInt());
        if (moving != null && moving.getX() < state.get("startX").getAsDouble() - .15) {
            clientMoved = true;
        }
        if (!state.get("done").getAsBoolean()) {
            job = server.submit(() -> session.poll());
            return false;
        }
        if (stage < 2) {
            // Native entity packets may trail the server's completed phase; keep the bounded poll alive.
            if (!clientMoved || !state.get("serverMoved").getAsBoolean()) {
                job = server.submit(() -> session.poll());
                return false;
            }
        }
        if (stage == 0) {
            if (count(client.player, Items.DIAMOND) != 3 || count(client.player, Items.GOLD_INGOT) != 4) {
                job = server.submit(() -> session.poll());
                return false;
            }
            require(state.get("delayChecked").getAsBoolean() && count(client.player, Items.DIRT) == 0,
                    "Delayed/rejected item controls were not preserved");
            if (client.level.getEntity(state.get("eligible").getAsInt()) != null
                    || client.level.getEntity(state.get("delayed").getAsInt()) != null) {
                return false;
            }
            if (rejoin || index < 2) {
                clientMoved = false;
                job = server.submit(() -> session.startStop());
                return false;
            }
        } else if (stage == 1) {
            job = server.submit(() -> session.idle());
            return false;
        } else {
            var idle = client.level.getEntity(state.get("idle").getAsInt());
            if (idle == null) {
                job = server.submit(() -> session.poll());
                return false;
            }
            require(Math.abs(idle.getX() - state.get("startX").getAsDouble()) < .01,
                    "Client newly spawned item attracted after selected-to-idle move");
        }
        report.append(rejoin ? "rejoined prepared Magnetic root" : SAMPLES.get(index))
              .append(": server/client motion; ordinary pickup diamond=3, gold=4; dirt=2 retained, delay observed")
              .append(rejoin || index < 2 ? "; selected-to-idle: new item stays put, existing momentum not reset" : "")
              .append('\n');
        NetworkAcceptanceFiles.write("silent-gear-magnet-" + (rejoin ? "rejoin" : "client") + "-report.txt", report.toString());
        if (rejoin || ++index == SAMPLES.size()) {
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(uuid);
                session.clean();
                owner.getInventory().clearContent();
                owner.getInventory().setItem(11, persisted);
                if (!rejoin) {
                    var saved = new JsonObject();
                    saved.addProperty("process", ProcessHandle.current().pid());
                    saved.addProperty("uuid", uuid.toString());
                    saved.add("root", encode(owner, persisted));
                    NetworkAcceptanceFiles.write("silent-gear-magnet-player.json", saved.toString());
                }
                return "{}";
            });
            // Await server cleanup/manifest publication before the parent saves or stops the client.
            completing = true;
            return false;
        }
        job = server.submit(() -> {
            session.clean();
            try {
                session = new Session(server.getPlayerList().getPlayer(uuid), SAMPLES.get(index), index == 6);
                return session.poll();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        clientMoved = false;
        return false;
    }

    private static com.google.gson.JsonElement encode(ServerPlayer owner, ItemStack root) {
        return ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow();
    }

    private static Path manifest() {
        return Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "silent-gear-magnet-player.json");
    }

    private static int count(Player owner, Item item) {
        int total = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack stack = owner.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private record Sample(boolean mixed, boolean greedy, EquipmentSlot slot) {
    }

    private static final class Session {
        private final ServerPlayer owner;
        private final ItemStack root;
        private final ItemStack before;
        private final Sample sample;
        private ItemEntity eligible;
        private ItemEntity delayed;
        private ItemEntity rejected;
        private ItemEntity moving;
        private ItemEntity idle;
        private int stage;
        private int started;
        private boolean delayChecked;
        private boolean serverMoved;

        private Session(ServerPlayer owner, Sample sample, boolean inverse) throws Exception {
            this.owner = owner;
            this.sample = sample;
            owner.getInventory().clearContent();
            owner.removeAllEffects();
            owner.getInventory().setSelectedSlot(0);
            for (var slot : EquipmentSlot.values()) {
                owner.setItemSlot(slot, ItemStack.EMPTY);
            }
            for (int x = 1; x <= 17; x++) {
                for (int z = 1; z <= 17; z++) {
                    owner.level().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            owner.setNoGravity(true);
            owner.connection.teleport(8.5, 81, 8.5, 0, 0);
            if (sample.greedy) {
                var nativeBoot = new NeoForgeSilentGearTest.NativeApi().boot("blaze_gold");
                root = sample.mixed
                       ? NeoForgeSilentGearTest.workbench(owner, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)).get(inverse ? 1 : 0)
                       : nativeBoot;
            } else {
                root = sample.mixed ? persisted : ModContent.getMixedBootsData(persisted).orElseThrow().left().source();
            }
            before = root.copy();
            owner.setItemSlot(sample.slot, root);
            eligible = drop(Items.DIAMOND, 3, 0);
            delayed = drop(Items.GOLD_INGOT, 4, -1);
            delayed.setPickUpDelay(60);
            rejected = drop(Items.DIRT, 2, 1);
            if (!sample.greedy) {
                rejected.getPersistentData().putBoolean("PreventRemoteMovement", true);
            }
            moving = eligible;
            started = owner.tickCount;
        }

        private ItemEntity drop(Item item, int count, double z) {
            var entity = new ItemEntity(owner.level(), 11.5, 81.25, 8.5 + z, new ItemStack(item, count));
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoPickUpDelay();
            require(owner.level().addFreshEntity(entity), "Could not spawn ordinary magnet item");
            return entity;
        }

        private String poll() {
            int elapsed = owner.tickCount - started;
            require(elapsed < 220, "Ordinary magnet timeout: " + sample + " stage=" + stage
                    + " diamond=" + count(owner, Items.DIAMOND) + " gold=" + count(owner, Items.GOLD_INGOT));
            if (sample.mixed) {
                require(ItemStack.matches(before, root), "Ordinary magnet ticks changed complete root/source state");
            }
            serverMoved |= moving.getX() < 11.35;
            boolean done;
            if (stage == 0) {
                require(rejected.isAlive() && rejected.getItem().getCount() == 2 && Math.abs(rejected.getX() - 11.5) < 1E-6
                                && count(owner, Items.DIRT) == 0, "Native filter/anti-movement control moved or vanished");
                if (elapsed >= 10 && elapsed < 45) {
                    require(delayed.isAlive() && Math.abs(delayed.getX() - 11.5) < 1E-6 && count(owner, Items.GOLD_INGOT) == 0,
                            "Pickup-delay item was attracted early");
                    delayChecked = true;
                }
                done = !eligible.isAlive() && !delayed.isAlive() && count(owner, Items.DIAMOND) == 3 && count(owner, Items.GOLD_INGOT) == 4;
            } else if (stage == 1) {
                done = elapsed >= 8 && serverMoved;
            } else {
                require(idle.isAlive() && Math.abs(idle.getX() - 11.5) < 1E-6 && count(owner, Items.AMETHYST_SHARD) == 0,
                        "New item attracted while boots are idle");
                done = elapsed >= 20;
            }

            var result = new JsonObject();
            result.addProperty("stage", stage);
            result.addProperty("done", done);
            result.addProperty("moving", moving.getId());
            result.addProperty("startX", 11.5);
            result.addProperty("serverMoved", serverMoved);
            result.addProperty("delayChecked", delayChecked);
            result.addProperty("eligible", eligible.getId());
            result.addProperty("delayed", delayed.getId());
            if (idle != null) {
                result.addProperty("idle", idle.getId());
            }

            return result.toString();
        }

        private String startStop() {
            moving = drop(Items.EMERALD, 1, 0);
            stage = 1;
            started = owner.tickCount;
            serverMoved = false;
            return poll();
        }

        private String idle() {
            owner.setItemSlot(sample.slot, ItemStack.EMPTY);
            owner.getInventory().setItem(10, root);
            idle = drop(Items.AMETHYST_SHARD, 1, -2);
            stage = 2;
            started = owner.tickCount;
            // The existing emerald keeps ordinary momentum. Only new attraction must stop, just as for native boots.
            return poll();
        }

        private void clean() {
            for (var entity : new ItemEntity[]{eligible, delayed, rejected, moving, idle}) {
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                }
            }
        }
    }
}
