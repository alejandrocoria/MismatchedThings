package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** One bounded session; files acknowledge observations, while ordinary game packets carry all item/attribute data. */
final class JustDireConnectedServer {
    private static int ticks;
    private static int selected = -1;
    private static int settled;
    private static boolean ready;
    private static boolean finished;
    private static ItemStack primary;
    private static ItemStack inverse;

    private JustDireConnectedServer() {}

    static Path report(String name) { return Path.of(System.getProperty("leggings.test.report"), name); }

    static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "JDT connected server timeout at " + selected);
            require(!Files.exists(report("client.failure.txt")), "Connected client failed");
            if (!ready && ticks > 20) {
                SkeletonChecks.verify();
                Files.writeString(report("ready.txt"), "Dedicated JDT production server ready\n");
                ready = true;
            }
            var players = server.getPlayerList().getPlayers();
            // The last observation may arrive in the same tick as the client's disconnect.
            // Consume that acknowledgement before requiring a player for another transition.
            if (selected == 7 && Files.exists(report("case-7.ok"))) {
                selected = 8;
            }
            if (selected == 8) {
                if (players.isEmpty() && Files.exists(report("client.ok"))) {
                    Files.writeString(report("connected.txt"), "Eight authoritative root transitions synchronized through ordinary packets; paid inverse roots, physical/Phase attributes, free movement and native consumers passed\n");
                    SkeletonChecks.passed("server");
                    finished = true;
                    server.halt(false);
                }
                return;
            }
            if (players.isEmpty()) { return; }
            var player = players.getFirst();
            player.setInvulnerable(true);
            player.setNoGravity(true);
            if (selected == -1) {
                seed(player);
                selected = 0;
                configure(player);
            }
            if (Files.exists(report("case-" + selected + ".ok"))) {
                selected++;
                if (selected < 8) { configure(player); }
                return;
            }
            if (++settled < 8) { return; }
            var worn = player.getItemBySlot(EquipmentSlot.LEGS);
            double expected = selected == 1 || selected == 2 || selected == 4 || selected == 6 ? 0 : 1;
            JustDirePassiveChecks.close(player.getAttributeValue(JDTRegistration.PHASE), expected, "Server worn Phase transition " + selected);
            require(worn.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0) == charge(selected), "Passive ability debited server FE");
            player.inventoryMenu.broadcastChanges();
            if (!Files.exists(report("case-" + selected + ".ready"))) {
                Files.writeString(report("case-" + selected + ".ready"), "Authoritative equipped state settled\n");
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    static int charge(int index) { return index == 1 ? 49 : index == 2 ? 0 : 50; }

    private static void configure(ServerPlayer player) {
        var root = selected == 7 ? inverse : primary;
        root.set(JustDireDataComponents.FORGE_ENERGY, charge(selected));
        root.set(JustDireDataComponents.TOOL_ENABLED, selected != 4);
        root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.PHASE), selected != 6);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("JDT connected " + selected));
        player.setItemSlot(EquipmentSlot.LEGS, root);
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        player.inventoryMenu.broadcastChanges();
        settled = 0;
    }

    private static void seed(ServerPlayer player) throws Exception {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.getInventory().clearContent();
        player.connection.teleport(.5, 70, .5, 0, 0);
        var source = JustDirePassiveChecks.nativeStack("eclipsealloy");
        source.set(JustDireDataComponents.FORGE_ENERGY, 100);
        var menu = new WorkbenchMenu(401, player.getInventory());
        menu.getSlot(0).set(source);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND_LEGGINGS));
        inverse = menu.getSlot(3).getItem().copy();
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        primary = menu.getCarried().copy();
        require(primary.has(LeggingsContent.mixedLeggingsDataType()) && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "Connected fixture was not paid");
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(player);
        require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, inverse), "Paid inverse companion missing");
        player.getInventory().clearContent();
        player.getInventory().setItem(1, inverse.copy());
        if (Boolean.getBoolean("leggings.test.jdtBoots")) {
            var nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse("justdirethings:eclipsealloy_boots")));
            nativeBoots.set(JustDireDataComponents.FORGE_ENERGY, 200);
            nativeBoots.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.STEPHEIGHT), true);
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            player.setItemSlot(EquipmentSlot.FEET, (ItemStack) pair.getClass().getMethod("primary").invoke(pair));
        }
    }
}
