package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireDecoy;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Natural production ticks cross two shortened boundary fixtures; packets, not markers, carry state. */
public final class JustDireHudServer {
    public static boolean captureSounds;
    public static final List<Identifier> sounds = new ArrayList<>();
    private static int ticks;
    private static int stage;
    private static boolean ready;
    private static boolean seeded;
    private static boolean finishing;
    private static boolean finished;
    private static ItemStack root;

    private JustDireHudServer() {}

    static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) {
            return;
        }
        try {
            require(++ticks < 3600, "Decoy HUD server timeout at " + stage);
            require(!Files.exists(JustDireConnectedServer.report("client.failure.txt")), "HUD client failed");
            if (!ready && ticks > 20) {
                JustDireFoundationChecks.graph();
                JustDireSettingsChecks.run(RepairFixtures.player(server));
                Files.writeString(JustDireConnectedServer.report("ready.txt"), "Independent settings remain healthy\n");
                ready = true;
            }
            if (seeded && Files.exists(JustDireConnectedServer.report("client.ok"))) {
                require(sounds.equals(List.of(Identifier.parse("minecraft:entity.evoker.prepare_summon"),
                        Identifier.parse("minecraft:block.conduit.deactivate"), Identifier.parse("minecraft:entity.ender_eye.death"))),
                        "Missing/duplicate native transition sounds: " + sounds);
                Files.writeString(JustDireConnectedServer.report("decoy-feedback.txt"), "Production action and natural active/inactive expiry emitted each native sound once; HUD failure did not affect action/clock/settings\n");
                Files.writeString(JustDireConnectedServer.report("connected.txt"), "Three ordinary root clock states and native HUD observed by independent client\n");
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
                return;
            }
            if (!ready || server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setInvulnerable(true);
            player.setNoGravity(true);
            if (!seeded) {
                player.getInventory().clearContent();
                root = JustDireDecoyInputChecks.root(false, true, 71, false, true);
                player.setItemSlot(EquipmentSlot.LEGS, root);
                if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                    var nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots")));
                    var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                    var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
                    var boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
                    boots.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("groundstomp", 100000, false)));
                    player.setItemSlot(EquipmentSlot.FEET, boots);
                }
                captureSounds = true;
                require(JustDireDecoy.activate(player, JustDireInputNetwork.legsSlot(), root)
                                && root.get(JustDireDataComponents.FORGE_ENERGY) == 5000, "HUD profile could not cast/pay production Decoy");
                player.inventoryMenu.broadcastChanges();
                Files.writeString(JustDireConnectedServer.report("case-0.ready"), "Real active Decoy and wallet published\n");
                seeded = true;
            }
            List<?> stored = root.getOrDefault(JustDireDataComponents.ABILITY_COOLDOWNS.get(), List.of());
            if (!finishing && Files.exists(JustDireConnectedServer.report("case-" + stage + ".ok"))) {
                if (stage == 2) {
                    finishing = true;
                    return;
                }
                root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1, stage == 0)));
                stage++;
                return; // Next normal worn tick, not a test call to the clock adapter, performs the transition.
            }
            if (stage == 1 && stored.size() == 1 && !((AbilityCooldown) stored.getFirst()).isactive()
                    || stage == 2 && stored.isEmpty()) {
                player.inventoryMenu.broadcastChanges();
                Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ready"), "Natural production phase transition\n");
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }
}
