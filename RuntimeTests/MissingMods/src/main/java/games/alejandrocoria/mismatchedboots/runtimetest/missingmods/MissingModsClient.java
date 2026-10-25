package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Future;

/** Three real connections in separate JVMs; native player/entity persistence and packets own the lifecycle. */
public final class MissingModsClient {
    private static final String STAGE = MissingModsProof.stage();
    private static final boolean ABSENT = STAGE.equals("clientabsent"), RESTORED = STAGE.equals("clientrestore");
    private static UUID playerId, droppedId;
    private static Vec3 deathPosition;
    private static Future<?> job;
    private static int phase, next, ticks, age;
    private static boolean finished;

    private MissingModsClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 5000, "Connected recovery timed out: " + phase);
            if (client.screen instanceof BackupConfirmScreen backup) {
                backup.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(
                        Component.translatable("selectWorld.backupJoinSkipButton"))).map(Button.class::cast).findFirst().orElseThrow()
                        .onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); return;
            }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            client.options.pauseOnLostFocus = false;
            if (job != null) {
                if (!job.isDone()) { return; }
                job.get(); job = null; phase = next; age = 0;
            }
            age++;
            if (phase == 0 && age >= 20) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Recovery client must be hidden/muted");
                playerId = client.player.getUUID();
                queue(client, () -> initialize(client), 1);
            } else if (phase == 1 && age >= 40) {
                var worn = client.player.getItemBySlot(EquipmentSlot.FEET);
                var expected = expected(client, RESTORED ? 3 : 2, RESTORED);
                require(ItemStack.matches(worn, expected), "Actual equipment packet differs from persisted root");
                require(ModContent.suspendedBootsData(worn).isPresent() == ABSENT, "Client has wrong suspended state");
                require(!ABSENT || client.player.getArmorValue() == 0, "Suspended client armor remains active");
                if (ABSENT) {
                    require(ItemStack.matches(client.player.getInventory().getItem(0), expected(client, 3, false)),
                            "Actual inventory packet lost inverse root");
                    queue(client, () -> {
                        var owner = owner(client);
                        liveEdit(owner.getItemBySlot(EquipmentSlot.FEET), 2);
                        liveEdit(owner.getInventory().getItem(0), 3);
                        owner.setInvulnerable(false);
                        deathPosition = owner.position();
                        require(owner.hurtServer(owner.level(), owner.damageSources().genericKill(), 1000)
                                && owner.isDeadOrDying(), "Native death did not occur");
                        var drops = owner.level().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(8));
                        require(drops.size() == 2, "Native death did not create exactly two roots");
                        // Pin the real drops while respawning: their random throw can leave the small platform.
                        drops.forEach(e -> { e.setNeverPickUp(); e.setNoGravity(true); e.setDeltaMovement(Vec3.ZERO); });
                    }, 2);
                } else { queue(client, () -> finishServer(client), 99); }
            } else if (phase == 2 && age >= 15 && client.player.isDeadOrDying()) {
                client.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                phase = 3; age = 0;
            } else if (phase == 3 && age >= 25 && !client.player.isDeadOrDying()) {
                queue(client, () -> {
                    var owner = owner(client);
                    var drops = owner.level().getEntitiesOfClass(ItemEntity.class,
                            new AABB(deathPosition.add(-8, -8, -8), deathPosition.add(8, 8, 8)));
                    require(drops.size() == 2, "Death lost/duplicated boots or refunded archived upgrades: " + drops.size()
                            + " at " + deathPosition + "; current=" + drops.stream().map(e -> e.getItem().getHoverName().getString() + " at " + e.position()).toList()
                            + "; feet=" + owner.getItemBySlot(EquipmentSlot.FEET).getHoverName().getString()
                            + "; inventory=" + owner.getInventory().getNonEquipmentItems().stream().filter(s -> !s.isEmpty()).toList());
                    drops.forEach(ItemEntity::setNeverPickUp);
                    var primary = drops.stream().filter(e -> ItemStack.matches(e.getItem(), expected(client, 2, true))).findFirst().orElseThrow();
                    var inverse = drops.stream().filter(e -> ItemStack.matches(e.getItem(), expected(client, 3, true))).findFirst().orElseThrow();
                    droppedId = primary.getUUID(); primary.setNoGravity(true);
                    owner.connection.teleport(16.5, 81, .5, 0, 0); owner.setNoGravity(true);
                    inverse.setNoPickUpDelay(); inverse.playerTouch(owner);
                    require(inverse.isRemoved(), "Native pickup failed");
                    require(ItemStack.matches(owner.getInventory().getItem(0), expected(client, 3, true)), "Death/pickup changed inverse root");
                    owner.setItemSlot(EquipmentSlot.FEET, owner.getInventory().getItem(0));
                    owner.getInventory().setItem(0, ItemStack.EMPTY);
                }, 4);
            } else if (phase == 4 && age >= 30) {
                require(ItemStack.matches(client.player.getItemBySlot(EquipmentSlot.FEET), expected(client, 3, true)),
                        "Respawn/equip packet lost suspended inverse root");
                queue(client, () -> finishServer(client), 99);
            } else if (phase == 99) {
                require(CaptureWitness.peerDecodes > 0, "Compatible capture wrapper absent from connected lifecycle");
                NetworkAcceptanceFiles.write("missing-mods-report.txt", STAGE
                        + ": real equipment/inventory packets; same-player different-JVM persistence; "
                        + (ABSENT ? "actual death/respawn/pickup, inverse re-equipped and primary dropped for reload" : RESTORED
                        ? "equipped inverse and dropped primary fully restored, live edits/resources preserved" : "active pair moved from barrel into real worn/player inventory") + "\n");
                NetworkAcceptanceFiles.write("missing-mods-success.txt", "passed\n");
                finished = true; client.stop();
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("missing-mods", failure); finished = true; client.stop();
        }
    }

    private static void initialize(Minecraft client) throws Exception {
        var owner = owner(client); var server = client.getSingleplayerServer();
        if (ABSENT || RESTORED) {
            var previous = NbtIo.readCompressed(manifest(), NbtAccounter.unlimitedHeap());
            require(previous.getLongOr("process", 0) != ProcessHandle.current().pid()
                    && previous.getStringOr("player", "").equals(playerId.toString()), "Reconnect reused JVM or changed player identity");
            require(previous.getStringOr("stage", "").equals(ABSENT ? "clientwrite" : "clientabsent"), "Connected phases ran out of order");
            if (RESTORED) { droppedId = UUID.fromString(previous.getStringOr("dropped", "")); }
        } else {
            owner.getInventory().clearContent();
            owner.setItemSlot(EquipmentSlot.FEET, MissingModsProof.storage(server).removeItemNoUpdate(2));
            owner.getInventory().setItem(0, MissingModsProof.storage(server).removeItemNoUpdate(3));
            MissingModsProof.storage(server).setChanged();
        }
        owner.setGameMode(GameType.SURVIVAL); owner.setNoGravity(true);
        server.getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
        for (int x = -2; x <= 2; x++) { for (int z = -2; z <= 2; z++) {
            owner.level().setBlock(new BlockPos(16 + x, 80, z), Blocks.STONE.defaultBlockState(), 3);
        } }
        owner.connection.teleport(16.5, 81, .5, 0, 0);
        require(ItemStack.matches(owner.getItemBySlot(EquipmentSlot.FEET), expected(client, RESTORED ? 3 : 2, RESTORED)),
                "Native player load changed worn source/root state");
    }

    private static void finishServer(Minecraft client) throws Exception {
        var owner = owner(client);
        if (RESTORED) {
            require(owner.level().getEntity(droppedId) instanceof ItemEntity drop
                    && ItemStack.matches(drop.getItem(), expected(client, 2, true)), "Saved death-drop entity did not fully recover");
            require(owner.getInventory().getNonEquipmentItems().stream().allMatch(ItemStack::isEmpty)
                    && owner.level().getEntitiesOfClass(ItemEntity.class, new AABB(-8, 77, -8, 24, 86, 8)).size() == 1,
                    "Reconnect duplicated/refunded archived resources");
        }
        var manifest = new CompoundTag(); manifest.putLong("process", ProcessHandle.current().pid());
        manifest.putString("stage", STAGE); manifest.putString("player", playerId.toString());
        if (droppedId != null) { manifest.putString("dropped", droppedId.toString()); }
        NbtIo.writeCompressed(manifest, manifest());
    }

    private static ItemStack expected(Minecraft client, int index, boolean edited) {
        var root = ItemStack.CODEC.parse(client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                MissingModsProof.readBaseline().getCompoundOrEmpty("root" + index)).getOrThrow();
        MissingModsProof.mutateWhileAbsent(root, index);
        if (edited) { liveEdit(root, index); }
        return root;
    }
    private static void liveEdit(ItemStack root, int index) {
        root.setDamageValue(root.getDamageValue() + 5);
        root.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("client absence edit " + index));
    }
    private static Path manifest() { return Path.of(System.getProperty("mismatchedboots.test.missingModsPlayer")); }
    private static ServerPlayer owner(Minecraft client) { return client.getSingleplayerServer().getPlayerList().getPlayer(playerId); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void queue(Minecraft client, Action action, int destination) {
        job = client.getSingleplayerServer().submit(() -> {
            try { action.run(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        next = destination;
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
