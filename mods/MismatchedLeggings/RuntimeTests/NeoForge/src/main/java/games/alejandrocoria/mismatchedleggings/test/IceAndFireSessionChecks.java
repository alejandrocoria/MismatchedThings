package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.iceandfire.DamageChecks;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real equipment/effect packets and disconnect/save/reopen of the same player/world. */
final class IceAndFireSessionChecks {
    private static int phase, age, ticks;
    private static boolean finished;
    private static boolean reopened;
    private static MinecraftServer previousServer;
    private static Object previousPlayer;
    private static UUID id;
    private static List<ItemStack> roots;
    private static ItemStack feet = ItemStack.EMPTY;
    private static CompletableFuture<Snapshot> job;
    private static final boolean BOOTS = Boolean.getBoolean("leggings.test.boots");

    private IceAndFireSessionChecks() {}

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Ice and Fire session timed out at " + phase);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client became visible");
            if (phase == 3) {
                if (client.player == null && client.level == null && client.getSingleplayerServer() == null) {
                    phase = 4;
                    age = 0;
                    client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                }
                return;
            }
            if (client.player == null || client.level == null || client.screen != null || client.getOverlay() != null) { return; }
            var server = client.getSingleplayerServer();
            if (phase == 4 && !reopened) {
                reopened = true;
                job = server.submit(() -> {
                    // Read this server's saved holders; never equip a snapshot from the previous registry lifetime.
                    try { roots = IceAndFireLifecycleChecks.reopen(server); }
                    catch (Exception failure) { throw new IllegalStateException("Reconnected world changed", failure); }
                    var player = server.getPlayerList().getPlayer(id);
                    require(ItemStack.matches(feet, player.getItemBySlot(EquipmentSlot.FEET)), "Reopen changed complete optional Boots history");
                    feet = player.getItemBySlot(EquipmentSlot.FEET).copy();
                    return snapshot(player);
                });
                return;
            }
            if (phase == 0) {
                id = client.player.getUUID();
                previousServer = server;
                previousPlayer = client.player;
                job = server.submit(() -> {
                    try { roots = IceAndFireLifecycleChecks.reopen(server); }
                    catch (Exception failure) { throw new IllegalStateException("Saved-world reopen failed", failure); }
                    var player = server.getPlayerList().getPlayer(id);
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                    player.setNoGravity(true);
                    for (int x = 1; x <= 4; x++) {
                        for (int z = 1; z <= 4; z++) {
                            for (int y = 79; y <= 85; y++) {
                                server.overworld().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                            }
                        }
                    }
                    if (BOOTS) {
                        feet = BootsCoexistence.pair(DamageChecks.nativeItem("tide_red_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst();
                    }
                    equip(player, 0);
                    return snapshot(player);
                });
                phase = 1;
                return;
            }
            if (job == null) { poll(server); return; }
            if (!job.isDone()) { return; }
            var state = job.join();
            job = null;
            var clientLegs = client.player.getItemBySlot(EquipmentSlot.LEGS);
            if (phase == 1 || phase == 2 || phase == 4 || phase == 6) {
                int rootIndex = phase == 1 || phase == 6 ? 0 : 1;
                require(ItemStack.matches(roots.get(rootIndex), state.legs()) && ItemStack.matches(feet, state.feet()),
                        "Equipment/history changed during connected Tide ticks");
                if (phase == 4) {
                    require(server != previousServer && client.player != previousPlayer && id.equals(client.player.getUUID()),
                            "Reconnect did not replace server/player and preserve UUID");
                }
                if (!ItemStack.matches(roots.get(rootIndex), clientLegs) || !ItemStack.matches(feet, client.player.getItemBySlot(EquipmentSlot.FEET))
                        || !state.breathing() || state.strength() != (BOOTS ? 1 : 0)
                        || !client.player.hasEffect(MobEffects.WATER_BREATHING) || !client.player.hasEffect(MobEffects.STRENGTH)
                        || client.player.getEffect(MobEffects.STRENGTH).getAmplifier() != (BOOTS ? 1 : 0)) {
                    poll(server); return;
                }
                if (++age < 12) { poll(server); return; }
                age = 0;
                if (phase == 1) {
                    phase = 2;
                    job = server.submit(() -> { equip(server.getPlayerList().getPlayer(id), 1); return snapshot(server.getPlayerList().getPlayer(id)); });
                } else if (phase == 2) {
                    client.disconnectWithSavingScreen();
                    phase = 3;
                } else {
                    int old = phase;
                    phase = old == 4 ? 5 : 7;
                    job = server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(id);
                        if (old == 4) {
                            player.getInventory().setItem(0, player.getItemBySlot(EquipmentSlot.LEGS));
                            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                            require(player.hasEffect(MobEffects.WATER_BREATHING), "Unequip forcibly removed native effects");
                        } else {
                            player.connection.teleport(10.5, 80, 10.5, 0, 0);
                        }
                        player.inventoryMenu.broadcastChanges();
                        return snapshot(player);
                    });
                }
            } else if (phase == 5) {
                require(state.legs().isEmpty() && state.feet().isEmpty() && ItemStack.matches(roots.get(1), state.carried()),
                        "Unequip mutated saved source history");
                if (++age < 80 || state.breathing() || state.strength() >= 0 || client.player.hasEffect(MobEffects.WATER_BREATHING)
                        || client.player.hasEffect(MobEffects.STRENGTH) || !clientLegs.isEmpty()) { poll(server); return; }
                phase = 6; age = 0;
                job = server.submit(() -> { equip(server.getPlayerList().getPlayer(id), 0); return snapshot(server.getPlayerList().getPlayer(id)); });
            } else if (phase == 7) {
                require(ItemStack.matches(roots.get(0), state.legs()) && ItemStack.matches(feet, state.feet()), "Dry transition mutated equipment");
                if (++age < 80 || state.strength() >= 0 || client.player.hasEffect(MobEffects.STRENGTH)) { poll(server); return; }
                require(state.breathing() && client.player.hasEffect(MobEffects.WATER_BREATHING), "Dry native Water Breathing stopped");
                Files.writeString(IceAndFireLifecycleChecks.report("ice-and-fire-session.txt"),
                        "Different JVM reopened 6 complete roots/history; saved Lightning/projectile consumers exercised;\n"
                                + "real equipment/effect packets, both Tide orientations, " + (BOOTS ? "two mixed pieces without originals, wet Strength II" : "standalone wet Strength I")
                                + "; native disconnect/save/reopen preserved UUID/equipment/history;\n"
                                + "unequip stopped refresh with natural expiry, inventory root inactive; re-equip resumed, drying retained breathing and expired Strength; dedicated multiplayer remains owner acceptance\n");
                finished = true;
                SkeletonChecks.passed("client");
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void equip(ServerPlayer player, int index) {
        player.setItemSlot(EquipmentSlot.LEGS, roots.get(index).copy());
        player.setItemSlot(EquipmentSlot.FEET, feet.copy());
        player.connection.teleport(2.5, 80, 2.5, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.inventoryMenu.broadcastChanges();
    }

    private static void poll(MinecraftServer server) { job = server.submit(() -> snapshot(server.getPlayerList().getPlayer(id))); }

    private static Snapshot snapshot(ServerPlayer player) {
        return new Snapshot(player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getItemBySlot(EquipmentSlot.FEET).copy(),
                player.getInventory().getItem(0).copy(), player.hasEffect(MobEffects.WATER_BREATHING),
                player.hasEffect(MobEffects.STRENGTH) ? player.getEffect(MobEffects.STRENGTH).getAmplifier() : -1);
    }

    private record Snapshot(ItemStack legs, ItemStack feet, ItemStack carried, boolean breathing, int strength) {}
}
