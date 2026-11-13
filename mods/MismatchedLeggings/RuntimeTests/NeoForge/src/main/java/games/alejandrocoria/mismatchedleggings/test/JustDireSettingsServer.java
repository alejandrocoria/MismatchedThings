package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Independent endpoint: marker files acknowledge observations, never transport stacks or edits. */
final class JustDireSettingsServer {
    private static int ticks;
    private static int stage;
    private static boolean ready;
    private static boolean seeded;
    private static boolean finished;
    private static ItemStack primary;
    private static ItemStack history;

    private JustDireSettingsServer() {}

    static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "Settings server timeout at " + stage);
            require(!Files.exists(JustDireConnectedServer.report("client.failure.txt")), "Settings client failed");
            if (!ready && ticks > 20) {
                SkeletonChecks.verify();
                JustDireSettingsChecks.run(RepairFixtures.player(server));
                Files.writeString(JustDireConnectedServer.report("ready.txt"), "Native menu security controls passed\n");
                ready = true;
            }
            if (seeded && Files.exists(JustDireConnectedServer.report("client.ok"))) {
                require(ItemStack.matches(history, primary.get(LeggingsContent.mixedLeggingsDataType()).left().source()), "GUI altered source history");
                if (!System.getProperty("leggings.test.jdtFault", "").equals("screen")) {
                    require(primary.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 4, "Canceled pending selection edited previous pair");
                }
                Files.writeString(JustDireConnectedServer.report("connected.txt"), "Actual native settings widgets, authoritative edit packets/replies, charge, inverse/native/sibling selections and reopen passed\n");
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
                return;
            }
            if (!ready || server.getPlayerList().getPlayers().isEmpty()) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setInvulnerable(true);
            player.setNoGravity(true);
            if (!seeded) {
                seed(player);
                open(player);
                seeded = true;
            }
            boolean fault = System.getProperty("leggings.test.jdtFault", "").equals("screen");
            if (stage == 0 && (fault || primary.getOrDefault(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 0) == 3
                    && !primary.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.PHASE), true)
                    && !primary.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY), true)
                    && primary.getOrDefault(JustDireDataComponents.ABILITY_BINDINGS.get(), List.<ToolRecords.AbilityBinding>of()).stream()
                    .anyMatch(record -> record.abilityName().equals("walkspeed") && record.key() == 4 && record.isMouse() && !record.requireEquipped())
                    && primary.getOrDefault(JustDireDataComponents.ABILITY_BINDINGS.get(), List.<ToolRecords.AbilityBinding>of()).stream()
                    .anyMatch(record -> record.abilityName().equals("decoy") && record.key() == 71 && !record.isMouse() && record.requireEquipped()))) {
                primary.set(JustDireDataComponents.FORGE_ENERGY, 888);
                player.inventoryMenu.broadcastChanges();
                Files.writeString(JustDireConnectedServer.report("case-0.ready"), "Root edits reached server; charging same pair\n");
                stage++;
            }
            if (stage == 1 && Files.exists(JustDireConnectedServer.report("case-4.ok")) && player.containerMenu == player.inventoryMenu) {
                open(player); // New native container, not a client-only reconstructed screen.
                stage++;
            }
            for (int index = 0; index < 8; index++) {
                if (Files.exists(JustDireConnectedServer.report("case-" + index + ".ok"))) {
                    if (index == 1 && !fault) { require(primary.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 4, "Charge-time edit not authoritative"); }
                    if (index == 2) { require(!player.getInventory().getItem(1).getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.WALKSPEED), true), "Native edit positive failed"); }
                    if (index == 3 && !fault) { require(player.getInventory().getItem(2).get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 2, "Inverse edit lost"); }
                }
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ToolSettingContainer(id, inventory, owner), Component.literal("JDT settings acceptance")));
    }

    private static void seed(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        var pair = MixedLeggingsFactory.createPair(JustDirePassiveChecks.nativeStack("eclipsealloy"), new ItemStack(Items.DIAMOND_LEGGINGS));
        primary = pair.primary();
        history = primary.get(LeggingsContent.mixedLeggingsDataType()).left().source().copy();
        player.getInventory().setItem(0, primary);
        player.getInventory().setItem(1, JustDirePassiveChecks.nativeStack("blazegold"));
        player.getInventory().setItem(2, pair.inverse());
        player.setItemSlot(EquipmentSlot.LEGS, pair.inverse().copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, primary.copy());
        if (Boolean.getBoolean("leggings.test.jdtBoots")) {
            var nativeBoots = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("justdirethings:eclipsealloy_boots")));
            nativeBoots.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.JUMPBOOST), true);
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var bootsPair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            player.getInventory().setItem(3, (ItemStack) bootsPair.getClass().getMethod("primary").invoke(bootsPair));
        }
        player.inventoryMenu.broadcastChanges();
    }
}
