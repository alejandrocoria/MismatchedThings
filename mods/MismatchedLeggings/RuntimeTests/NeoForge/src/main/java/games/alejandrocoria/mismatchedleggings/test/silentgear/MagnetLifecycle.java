package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Dedicated acceptance driven only by normal player/item ticks and SG's registered queue listener. */
public final class MagnetLifecycle {
    static final int SAMPLES = 6;
    static final double START_X = 11.5;
    private static int ticks;
    private static int step;
    private static int phase;
    private static int started;
    private static boolean finished;
    private static boolean initialized;
    private static boolean delayChecked;
    private static boolean serverMoved;
    private static boolean pathChecked;
    private static ItemStack root;
    private static ItemStack before;
    private static ItemStack boots = ItemStack.EMPTY;
    private static ItemStack bootsBefore;
    private static ItemEntity eligible;
    private static ItemEntity delayed;
    private static ItemEntity rejected;
    private static ItemEntity idle;
    private static final Vec3[] nativePaths = new Vec3[2];
    private static final StringBuilder REPORT = new StringBuilder();

    private MagnetLifecycle() {}

    static String label(int sample) {
        return (sample < 3 ? "Magnetic" : "Greedy") + "/" + List.of("native", "primary", "inverse").get(sample % 3);
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "Connected magnet server timeout: step=" + step + " phase=" + phase);
            require(!Files.exists(StateChecks.report("client.failure.txt")), "Connected magnet client failed");
            if (!initialized) {
                if (ticks < 20) { return; }
                require(ModList.get().isLoaded("silentgear") && SilentGearMagnet.available(), "Native/production magnet readiness missing");
                require(ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.boots"), "Wrong connected magnet graph");
                for (int x = 1; x <= 17; x++) {
                    for (int z = 1; z <= 17; z++) { server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3); }
                }
                Files.writeString(StateChecks.report("ready.txt"), "Dedicated magnet world ready\n");
                initialized = true;
            }
            var players = server.getPlayerList().getPlayers();
            if (step == SAMPLES) {
                if (players.isEmpty() && Files.exists(StateChecks.report("client.ok"))) {
                    Files.writeString(StateChecks.report("magnet-server.txt"), REPORT);
                    Files.writeString(StateChecks.report("state.txt"), "Bounded connected magnet acceptance, not the state lifecycle matrix\n");
                    SkeletonChecks.passed("server");
                    finished = true;
                    server.halt(false);
                }
                return;
            }
            if (players.isEmpty() || !Files.exists(StateChecks.report("magnet-client-ready.ok"))) { return; }
            var player = players.getFirst();
            if (phase == 0) {
                prepare(player);
                phase = 1;
            } else if (phase == 1 && Files.exists(StateChecks.report("magnet-" + step + ".equipment.ok"))) {
                require(Math.abs(player.getX() - 8.5) < .01 && Math.abs(player.getY() - 81) < .01, "Dedicated teleport did not settle");
                eligible = drop(player, Items.DIAMOND, 3, 0);
                delayed = drop(player, Items.GOLD_INGOT, 4, -1);
                delayed.setPickUpDelay(60);
                rejected = drop(player, Items.DIRT, 2, 1);
                if (step < 3) { rejected.getPersistentData().putBoolean("PreventRemoteMovement", true); }
                var ids = new JsonObject();
                ids.addProperty("eligible", eligible.getId());
                ids.addProperty("delayed", delayed.getId());
                ids.addProperty("rejected", rejected.getId());
                Files.writeString(StateChecks.report("magnet-" + step + ".items.json"), ids.toString());
                started = player.tickCount;
                phase = 2;
            } else if (phase == 2) {
                active(player);
                if (Files.exists(StateChecks.report("magnet-" + step + ".active.ok"))) {
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    player.getInventory().setItem(8, root);
                    player.getInventory().setItem(9, boots);
                    publishEquipment(player, "idle");
                    idle = drop(player, Items.EMERALD, 1, -2);
                    Files.writeString(StateChecks.report("magnet-" + step + ".idle-id.txt"), Integer.toString(idle.getId()));
                    started = player.tickCount;
                    phase = 3;
                }
            } else if (phase == 3) {
                require(ItemStack.matches(before, root) && ItemStack.matches(bootsBefore, boots), "Idle ticks changed roots/sources");
                require(idle.isAlive() && Math.abs(idle.getX() - START_X) < 1E-6 && count(player, Items.EMERALD) == 0,
                        "New item attracted after equipped-to-idle move");
                if (player.tickCount - started < 25) { return; }
                Files.writeString(StateChecks.report("magnet-" + step + ".idle.settled"), "25 ordinary idle ticks, new item remains still horizontally\n");
                if (!Files.exists(StateChecks.report("magnet-" + step + ".ok"))) { return; }
                REPORT.append(label(step)).append(": native-equivalent early motion, ordinary diamond=3/gold=4 pickup; dirt/delay negatives, idle stop and unchanged roots; overlap=")
                        .append(!boots.isEmpty()).append('\n');
                clean();
                phase = 0;
                if (++step == SAMPLES) { Files.writeString(StateChecks.report("finish.txt"), "All connected magnet samples accepted\n"); }
            }
        } catch (Throwable failure) {
            finished = true;
            clean();
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void prepare(ServerPlayer player) throws Exception {
        clean();
        player.getInventory().clearContent();
        player.removeAllEffects();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.getInventory().setSelectedSlot(0);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        player.connection.teleport(8.5, 81, 8.5, 0, 0);
        ItemStack source = step < 3 ? MagnetChecks.magnetic(FoundationChecks.armor("iron"), player) : FoundationChecks.armor("blaze_gold");
        root = step % 3 == 0 ? source : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).get(step % 3 - 1);
        boots = ItemStack.EMPTY;
        if (step == 1 && Boolean.getBoolean("leggings.test.boots")) {
            var bootSource = MagnetChecks.magnetic(WielderChecks.armor("boots", "boot_plates", "iron"), player);
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, bootSource, new ItemStack(Items.DIAMOND_BOOTS));
            boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        }
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        before = root.copy();
        bootsBefore = boots.copy();
        delayChecked = false;
        serverMoved = false;
        pathChecked = false;
        publishEquipment(player, "equipment");
    }

    private static void active(ServerPlayer player) throws Exception {
        int elapsed = player.tickCount - started;
        require(elapsed < 220, "Ordinary movement/pickup timeout: " + label(step));
        require(ItemStack.matches(before, root) && ItemStack.matches(bootsBefore, boots), "Active ticks changed complete roots/sources");
        serverMoved |= eligible.getX() < START_X - .15;
        if (elapsed == 6) {
            if (step % 3 == 0) { nativePaths[step / 3] = eligible.position(); }
            else { require(eligible.position().distanceToSqr(nativePaths[step / 3]) < 1E-10, "Natural mixed trajectory differs from native: " + label(step)); }
            pathChecked = true;
        }
        require(rejected.isAlive() && rejected.getItem().getCount() == 2 && Math.abs(rejected.getX() - START_X) < 1E-6
                && count(player, Items.DIRT) == 0, "Native Greedy filter/remote movement control failed");
        if (elapsed >= 10 && elapsed < 45) {
            require(delayed.isAlive() && Math.abs(delayed.getX() - START_X) < 1E-6 && count(player, Items.GOLD_INGOT) == 0,
                    "Pickup-delay control moved/was picked up too early");
            delayChecked = true;
        }
        if (!eligible.isAlive() && !delayed.isAlive() && count(player, Items.DIAMOND) == 3 && count(player, Items.GOLD_INGOT) == 4) {
            require(serverMoved && delayChecked && pathChecked, "Natural trajectory/positive/delay controls were not observed");
            Files.writeString(StateChecks.report("magnet-" + step + ".active.settled"), "Normal server ticks moved and picked up exact item counts\n");
        }
    }

    private static void publishEquipment(ServerPlayer player, String phase) throws Exception {
        player.inventoryMenu.broadcastChanges();
        NbtIo.writeCompressed(snapshot(player), StateChecks.report("magnet-" + step + "." + phase + ".nbt"));
        Files.writeString(StateChecks.report("magnet-" + step + "." + phase + ".ready"), label(step));
    }

    static CompoundTag snapshot(Player player) {
        var tag = new CompoundTag();
        tag.put("legs", StateChecks.encode(player.getItemBySlot(EquipmentSlot.LEGS), player.registryAccess()));
        tag.put("feet", StateChecks.encode(player.getItemBySlot(EquipmentSlot.FEET), player.registryAccess()));
        tag.put("idleLegs", StateChecks.encode(player.getInventory().getItem(8), player.registryAccess()));
        tag.put("idleBoots", StateChecks.encode(player.getInventory().getItem(9), player.registryAccess()));
        return tag;
    }

    static int count(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(item)) { count += stack.getCount(); }
        }
        return count;
    }

    private static ItemEntity drop(ServerPlayer player, Item item, int count, double z) {
        var entity = new ItemEntity(player.level(), START_X, 81.25, 8.5 + z, new ItemStack(item, count));
        entity.setDeltaMovement(Vec3.ZERO); // Initial fixture setup only; all subsequent motion is native.
        entity.setNoPickUpDelay();
        require(player.level().addFreshEntity(entity), "Could not spawn ordinary connected magnet item");
        return entity;
    }

    private static void clean() {
        for (var item : new ItemEntity[]{eligible, delayed, rejected, idle}) {
            if (item != null && item.isAlive()) { item.discard(); }
        }
        eligible = null;
        delayed = null;
        rejected = null;
        idle = null;
    }
}
