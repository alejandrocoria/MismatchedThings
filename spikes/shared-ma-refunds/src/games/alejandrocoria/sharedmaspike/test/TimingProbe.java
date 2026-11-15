package games.alejandrocoria.sharedmaspike.test;

import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundControl;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Compare current packaged production with the candidate, never an old release graph. */
public final class TimingProbe {
    private static boolean finished;

    private TimingProbe() {
    }

    public static void coreTick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            SpikeChecks.verifyTransform(false);
            var profile = new GameProfile(UUID.randomUUID(), "shared-ma-core");
            var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
            new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
            var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            loaded.setAccessible(true); loaded.invoke(player.connection);
            player.setGameMode(GameType.SURVIVAL);
            var root = new ItemStack(Items.IRON_LEGGINGS);
            player.setItemSlot(EquipmentSlot.LEGS, root);
            root.setDamageValue(root.getMaxDamage() - 1);
            int[] callbacks = {0};
            root.hurtAndBreak(2, player.level(), player, item -> callbacks[0]++);
            SkeletonChecks.require(root.isEmpty() && callbacks[0] == 1, "Unclaimed core-only consumption");
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "refunds.txt"),
                    "Things + MA without either product; one ready shared observer and ordinary unclaimed break/callback\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { server.halt(false); }
    }

    public static void tick(MinecraftServer server, boolean baseline) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            if (!baseline) { SpikeChecks.initialize(); SpikeChecks.verifyTransform(false); }
            var report = new StringBuilder();
            for (boolean leggings : List.of(false, true)) {
                for (boolean throwing : List.of(false, true)) {
                    String product = leggings ? "mismatchedleggings" : "mismatchedboots";
                    String piece = leggings ? "leggings" : "boots";
                    var factory = Class.forName("games.alejandrocoria." + product + ".composition.Mixed" + (leggings ? "Leggings" : "Boots") + "Factory");
                    Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null,
                            NativeAugments.item("inferium_" + piece, "HEALTH_BOOST_I"), new ItemStack(leggings ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS));
                    var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
                    var fixture = Class.forName("games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundChecks");
                    var makePlayer = fixture.getDeclaredMethod("player", MinecraftServer.class);
                    makePlayer.setAccessible(true);
                    var player = (ServerPlayer) makePlayer.invoke(null, server);
                    player.setItemSlot(leggings ? EquipmentSlot.LEGS : EquipmentSlot.FEET, root);
                    root.remove(DataComponents.UNBREAKABLE);
                    root.setDamageValue(root.getMaxDamage() - 1);
                    RefundControl.reset(root, player.getInventory());
                    int[] callbacks = {0};
                    boolean threw = false;
                    try {
                        root.hurtAndBreak(2, player.level(), player, item -> {
                            callbacks[0]++;
                            if (throwing) { throw new IllegalStateException("timing callback"); }
                        });
                    } catch (IllegalStateException failure) {
                        if (!failure.getMessage().equals("timing callback")) { throw failure; }
                        threw = true;
                    }
                    Item augment = (Item) NativeAugments.invoke(NativeAugments.augment("HEALTH_BOOST_I"), "getItem", new Class<?>[0]);
                    int returned = player.getInventory().countItem(augment);
                    int expected = throwing && (!baseline || leggings) ? 0 : 1;
                    SkeletonChecks.require(root.isEmpty() && callbacks[0] == 1 && threw == throwing && returned == expected,
                            "Timing mismatch " + product + " baseline=" + baseline + " throwing=" + throwing + " refund=" + returned);
                    report.append(product).append(" baseline=").append(baseline).append(" throwingCallback=")
                            .append(throwing).append(" returned=").append(returned).append('\n');
                }
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "refunds.txt"), report.toString());
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { RefundControl.reset(null, null); server.halt(false); }
    }
}
