package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native disconnect/save/open flow: a new integrated server/player, not an entity-codec simulation. */
final class AquacultureSessionChecks {
    private static int phase;
    private static int age;
    private static MinecraftServer previousServer;
    private static Object previousClientPlayer;
    private static UUID playerId;
    private static ItemStack expected;
    private static CompletableFuture<Snapshot> job;
    private static Snapshot snapshot;
    private static double unequippedY;

    private AquacultureSessionChecks() {
    }

    static boolean tick(Minecraft client) throws Exception {
        if (phase == 2) {
            if (client.player == null && client.level == null && client.getSingleplayerServer() == null) {
                phase = 3;
                client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
            }
            return false;
        }
        if (client.player == null || client.level == null || client.getOverlay() != null || client.screen != null) {
            return false;
        }
        var server = client.getSingleplayerServer();
        if (phase == 0) {
            previousServer = server;
            previousClientPlayer = client.player;
            playerId = client.player.getUUID();
            var source = AquacultureRenderChecks.source(client, true, false);
            expected = AquacultureChecks.pair(source, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
            expected.set(DataComponents.CUSTOM_NAME, Component.literal("Aquaculture session root"));
            expected.setDamageValue(13);
            job = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.LEGS, expected.copy());
                player.inventoryMenu.broadcastChanges();
                player.connection.teleport(2.5, 80, 2.5, 0, 0);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
                return snapshot(player);
            });
            phase = 1;
            return false;
        }
        if (phase == 3) {
            require(server != previousServer && client.player != previousClientPlayer && client.player.getUUID().equals(playerId),
                    "Reopen did not replace the server/player while preserving identity");
            phase = 4;
            age = 0;
        }
        if (job != null) {
            if (!job.isDone()) {
                return false;
            }
            snapshot = job.join();
            job = null;
        }
        if (snapshot == null) {
            poll(server);
            return false;
        }
        if (phase == 1 || phase == 4) {
            require(ItemStack.matches(expected, snapshot.legs()), "Save/reopen changed complete live root or source history");
            if (!ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.LEGS))
                    || !client.player.isEyeInFluid(FluidTags.WATER) || !snapshot.eyesInWater()) {
                poll(server);
                return false;
            }
            require(!snapshot.noGravity() && !client.player.isNoGravity()
                            && snapshot.gravity() == 0 && client.player.getGravity() == 0,
                    "Real saved-world session lost buoyancy or acquired persistent NoGravity");
            if (++age < 30) {
                poll(server);
                return false;
            }
            if (phase == 1) {
                snapshot = null;
                client.disconnectWithSavingScreen();
                phase = 2;
            } else {
                unequippedY = client.player.getY();
                job = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    player.inventoryMenu.broadcastChanges();
                    return snapshot(player);
                });
                phase = 5;
                age = 0;
            }
        } else if (phase == 5) {
            if (!client.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || ++age < 20) {
                poll(server);
                return false;
            }
            require(snapshot.legs().isEmpty() && snapshot.gravity() > 0 && client.player.getGravity() > 0
                            && !snapshot.noGravity() && !client.player.isNoGravity() && client.player.getY() < unequippedY - .1,
                    "Unequipping after real reopen retained stale buoyancy");
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "aquaculture-session.txt"),
                    "Native disconnect/save/reopen created a new integrated server/player with the same UUID;\n"
                    + "complete worn root (trim/name/damage/history) persisted; client/server buoyancy resumed without NoGravity;\n"
                    + "actual unequip packet restored ordinary gravity and sinking; dedicated multiplayer remains owner acceptance\n");
            return true;
        }
        return false;
    }

    private static void poll(MinecraftServer server) {
        job = server.submit(() -> snapshot(server.getPlayerList().getPlayer(playerId)));
    }

    @SuppressWarnings("deprecation")
    private static Snapshot snapshot(ServerPlayer player) {
        return new Snapshot(player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getGravity(), player.isNoGravity(),
                player.isEyeInFluid(FluidTags.WATER));
    }

    private record Snapshot(ItemStack legs, double gravity, boolean noGravity, boolean eyesInWater) { }
}
