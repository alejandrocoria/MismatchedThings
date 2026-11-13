package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.entities.DecoyEntity;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityBinding;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Request/reply admission on real roots, separate from the independent client's physical input controls. */
final class JustDireDecoyInputChecks {
    private static int sequence;

    private JustDireDecoyInputChecks() {}

    static ItemStack root(boolean duplicate, boolean inverse, int key, boolean mouse, boolean equipped) {
        var nativeStack = JustDirePassiveChecks.nativeStack("eclipsealloy");
        var pair = MixedLeggingsFactory.createPair(nativeStack, duplicate ? nativeStack.copy() : new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireDataComponents.FORGE_ENERGY, 10000);
        root.set(JustDireDataComponents.ABILITY_BINDING_MODES.get(Ability.DECOY), 2);
        root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new AbilityBinding("decoy", key, mouse, equipped)));
        return root;
    }

    static List<DecoyEntity> entities(ServerPlayer player) {
        return player.level().getEntitiesOfClass(DecoyEntity.class, new AABB(player.position(), player.position()).inflate(16));
    }

    static void clear(ServerPlayer player) {
        entities(player).forEach(DecoyEntity::discard);
    }

    static void run(ServerPlayer player) throws Exception {
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        var root = root(true, false, 71, false, false);
        root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new AbilityBinding("decoy", 71, false, false),
                new AbilityBinding("decoy", 71, false, false), new AbilityBinding("walkspeed", 71, false, false)));
        root.set(JustDireDataComponents.ABILITY_BINDING_MODES.get(Ability.WALKSPEED), 2);
        player.getInventory().setItem(0, root);
        var history = root.get(LeggingsContent.mixedLeggingsDataType());
        var reply = prepare(player, 71, false);
        require(reply.slots().equals(List.of(0)), "Decoy bound action not discovered");
        var action = action(reply, 71, false, List.of(0));
        JustDireInputNetwork.handle(player, action);
        require(root.get(JustDireDataComponents.FORGE_ENERGY) == 5000 && entities(player).size() == 1
                        && root.has(JustDireDataComponents.ABILITY_COOLDOWNS)
                        && root.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY), true)
                        && !root.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.WALKSPEED), true)
                        && history.equals(root.get(LeggingsContent.mixedLeggingsDataType())),
                "Shared key duplicated active action or toggled Decoy instead of the passive");
        JustDireInputNetwork.handle(player, action);
        send(player, 71, false);
        require(root.get(JustDireDataComponents.FORGE_ENERGY) == 5000 && entities(player).size() == 1, "Replay/cooldown caused another cast");
        clear(player);

        player.getInventory().clearContent();
        root = root(false, false, 71, false, false);
        var inverse = root(false, true, 71, false, false);
        player.getInventory().setItem(0, root);
        player.getInventory().setItem(2, inverse);
        reply = prepare(player, 71, false);
        require(reply.slots().equals(List.of(0, 2)), "Independent roots were not batched");
        JustDireInputNetwork.handle(player, action(reply, 71, false, reply.slots()));
        require(root.get(JustDireDataComponents.FORGE_ENERGY) == 5000 && inverse.get(JustDireDataComponents.FORGE_ENERGY) == 5000
                && entities(player).size() == 2, "Batched roots shared a wallet/clock or lost one action");
        clear(player);

        for (String condition : List.of("replacement", "binding", "move", "global", "ability", "charge", "clock", "config", "duplicate", "expanded")) {
            player.getInventory().clearContent();
            root = root(false, false, 71, false, false);
            player.getInventory().setItem(0, root);
            reply = prepare(player, 71, false);
            require(reply.slots().equals(List.of(0)), "Negative control has no positive target: " + condition);
            var config = Config.AVAILABLE_ABILITY_MAP.get(Ability.DECOY);
            boolean previous = config.get();
            try {
                switch (condition) {
                    case "replacement" -> player.getInventory().setItem(0, root.copy());
                    case "binding" -> root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new AbilityBinding("decoy", 72, false, false)));
                    case "move" -> {
                        player.getInventory().setItem(0, ItemStack.EMPTY);
                        player.setItemSlot(EquipmentSlot.LEGS, root);
                    }
                    case "global" -> root.set(JustDireDataComponents.TOOL_ENABLED, false);
                    case "ability" -> root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY), false);
                    case "charge" -> root.set(JustDireDataComponents.FORGE_ENERGY, 4999);
                    case "clock" -> root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1, false)));
                    case "config" -> config.set(false);
                    default -> { /* Malformed slot batches retain an otherwise valid positive control. */ }
                }
                var before = root.copy();
                var slots = condition.equals("duplicate") ? List.of(0, 0) : condition.equals("expanded") ? List.of(0, 2) : List.of(0);
                JustDireInputNetwork.handle(player, action(reply, 71, false, slots));
                require(ItemStack.matches(root, before) && entities(player).isEmpty(), "Invalid input spent/spawned: " + condition);
                if (condition.equals("replacement")) {
                    require(ItemStack.matches(player.getInventory().getItem(0), before), "Equal replacement activated");
                }
            } finally { config.set(previous); }
        }

        player.getInventory().clearContent();
        root = root(false, true, 4, true, true);
        player.getInventory().setItem(0, root);
        require(prepare(player, 4, true).slots().isEmpty(), "Equipped-only action admitted stored root");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        send(player, 4, true);
        require(root.get(JustDireDataComponents.FORGE_ENERGY) == 5000 && entities(player).size() == 1, "Actual mixed LEGS slot did not satisfy equipped binding");
        clear(player);
        root.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
        var before = root.copy();
        require(prepare(player, 0, true).slots().isEmpty() && prepare(player, 1, true).slots().isEmpty()
                        && JustDireSettings.boundPassives(root, 4, true, true).isEmpty() && ItemStack.matches(root, before),
                "Normal mouse buttons or Decoy passive dispatch accepted");
        player.getInventory().clearContent();
        Files.writeString(JustDireConnectedServer.report("decoy-input-security.txt"),
                "Production Decoy batch dispatch, shared passive key, duplicates/replay/stale identity/move/binding/location/config/switch/FE/clock and mouse exclusions passed\n");
    }

    private static JustDireInputNetwork.InputReply prepare(ServerPlayer player, int key, boolean mouse) {
        return JustDireInputNetwork.handle(player, new JustDireInputNetwork.InputRequest(++sequence, key, mouse, JustDireSettingsNetwork.NONE, List.of()));
    }

    private static JustDireInputNetwork.InputRequest action(JustDireInputNetwork.InputReply reply, int key, boolean mouse, List<Integer> slots) {
        return new JustDireInputNetwork.InputRequest(sequence, key, mouse, reply.token(), slots);
    }

    private static void send(ServerPlayer player, int key, boolean mouse) {
        var reply = prepare(player, key, mouse);
        JustDireInputNetwork.handle(player, action(reply, key, mouse, reply.slots()));
    }
}
