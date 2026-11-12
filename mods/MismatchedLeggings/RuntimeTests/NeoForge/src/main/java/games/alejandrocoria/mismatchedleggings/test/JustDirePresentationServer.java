package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class JustDirePresentationServer {
    private static int ticks;
    private static int stage;
    private static boolean ready;
    private static boolean seeded;
    private static boolean finished;
    private static ItemStack main;
    private static ItemStack inverse;
    private static ItemStack worn;
    private static ItemStack offhand;
    private static ItemStack boots;

    private JustDirePresentationServer() {}

    static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4000, "JDT presentation server timeout at " + stage);
            require(!Files.exists(JustDireConnectedServer.report("client.failure.txt")), "Presentation client failed");
            if (!ready && ticks > 20) {
                JustDireFoundationChecks.graph();
                JustDireInputChecks.run(RepairFixtures.player(server));
                Files.writeString(JustDireConnectedServer.report("ready.txt"), "Input security passed\n");
                ready = true;
            }
            if (seeded && Files.exists(JustDireConnectedServer.report("client.ok"))) {
                Files.writeString(JustDireConnectedServer.report("connected.txt"), "Independent keyboard/mouse passive batches, actual rebindings and held sibling/native priority passed\n");
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
                return;
            }
            if (!ready || server.getPlayerList().getPlayers().isEmpty()) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setInvulnerable(true);
            player.setNoGravity(true);
            if (!seeded) { seed(player); seeded = true; }
            if (!Files.exists(JustDireConnectedServer.report("case-" + stage + ".ok"))) { return; }
            if (stage == 0) {
                require(JustDireInputChecks.on(main, Ability.WALKSPEED), "Release/repeat/GUI toggled");
            } else if (stage == 1) {
                require(!JustDireInputChecks.on(main, Ability.WALKSPEED) && JustDireInputChecks.on(main, Ability.RUNSPEED)
                        && !JustDireInputChecks.on(inverse, Ability.WALKSPEED)
                        && !JustDireInputChecks.on(worn, Ability.RUNSPEED)
                        && !JustDireInputChecks.on(worn, Ability.WALKSPEED)
                        && JustDireInputChecks.on(offhand, Ability.PHASE), "Keyboard batch/equipment mismatch");
            } else if (stage == 2) {
                require(!JustDireInputChecks.on(inverse, Ability.SWIMSPEED) && !JustDireInputChecks.on(offhand, Ability.SWIMSPEED), "Inventory mouse batch failed");
            } else if (stage == 3) {
                require(main.getOrDefault(JustDireDataComponents.TOOL_ENABLED, false), "Rebound global key failed");
            } else if (stage == 4) {
                require(!main.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true)
                        && !offhand.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true), "Mouse toggle or main-hand priority failed");
                player.getInventory().setItem(0, JustDirePassiveChecks.nativeStack("eclipsealloy"));
            } else if (stage == 5) {
                require(!offhand.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true), "Native-held priority lost");
                player.getInventory().setItem(0, boots == null ? ItemStack.EMPTY : boots);
            } else if (stage == 6) {
                require(offhand.getOrDefault(JustDireDataComponents.TOOL_ENABLED, false), "Offhand mixed fallback failed");
                if (boots != null) { require(!boots.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true), "Boots independent toggle lost"); }
            } else if (stage == 7) {
                require(!JustDireInputChecks.on(inverse, Ability.WALKSPEED), "GUI passive input was not suppressed");
            }
            player.inventoryMenu.broadcastChanges();
            Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ready"), "Authoritative stage " + stage + " passed\n");
            stage++;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void seed(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        main = JustDireInputChecks.root();
        inverse = main.copy();
        worn = main.copy();
        offhand = main.copy();
        JustDireInputChecks.bind(main, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, false),
                new ToolRecords.AbilityBinding("runspeed", 71, false, true)));
        JustDireInputChecks.bind(inverse, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, false),
                new ToolRecords.AbilityBinding("swimspeed", 4, true, false)));
        JustDireInputChecks.bind(worn, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, true),
                new ToolRecords.AbilityBinding("runspeed", 71, false, true)));
        JustDireInputChecks.bind(offhand, List.of(new ToolRecords.AbilityBinding("swimspeed", 4, true, false),
                new ToolRecords.AbilityBinding("phase", 71, false, true)));
        player.getInventory().setItem(0, main);
        player.getInventory().setItem(2, inverse);
        player.setItemSlot(EquipmentSlot.LEGS, worn);
        player.setItemSlot(EquipmentSlot.OFFHAND, offhand);
        if (Boolean.getBoolean("leggings.test.jdtBoots")) {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var nativeBoots = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("justdirethings:eclipsealloy_boots")));
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
            player.getInventory().setItem(3, boots);
        }
        player.inventoryMenu.broadcastChanges();
    }
}
