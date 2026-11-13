package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireHeldToggleCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.NONE;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Server admission, stale identities and passive state are independent of client UI behavior. */
final class JustDireInputChecks {
    private static int sequence;
    private static int preparedKey;
    private JustDireInputChecks() {}

    static ItemStack root() {
        var root = MixedLeggingsFactory.createPair(JustDirePassiveChecks.nativeStack("eclipsealloy"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        root.set(JustDireDataComponents.FORGE_ENERGY, 0);
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        bind(root, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, false)));
        return root;
    }

    static void bind(ItemStack root, List<ToolRecords.AbilityBinding> records) {
        root.set(JustDireDataComponents.ABILITY_BINDINGS, records);
        for (var ability : List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED, Ability.PHASE, Ability.DECOY)) {
            root.set(JustDireDataComponents.ABILITY_BINDING_MODES.get(ability), 2);
        }
    }

    static boolean on(ItemStack root, Ability ability) {
        return root.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(ability), true);
    }

    static void run(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        player.containerMenu = player.inventoryMenu;
        var root = root();
        bind(root, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, false),
                new ToolRecords.AbilityBinding("walkspeed", 71, false, false)));
        player.getInventory().setItem(0, root);
        var inverse = root.copy();
        player.getInventory().setItem(2, inverse);
        var before = root.copy();
        var reply = prepare(player, 71, false);
        require(reply.slots().equals(List.of(0, 2)), "Two disabled/depleted roots not admitted");
        var activate = new JustDireInputNetwork.InputRequest(sequence, 71, false, reply.token(), reply.slots());
        JustDireInputNetwork.handle(player, roundtrip(player, activate));
        require(!on(root, Ability.WALKSPEED) && !on(inverse, Ability.WALKSPEED), "Passive batch did not toggle both roots");
        JustDireInputNetwork.handle(player, activate);
        require(!on(root, Ability.WALKSPEED), "Nonce replay toggled a second time");
        require(root.get(JustDireDataComponents.FORGE_ENERGY) == 0
                && ItemStack.matches(before.get(LeggingsContent.mixedLeggingsDataType()).left().source(), root.get(LeggingsContent.mixedLeggingsDataType()).left().source()), "Input spent FE or changed history");

        reply = prepare(player, 71, false);
        var replaced = root.copy();
        player.getInventory().setItem(0, replaced);
        activate(player, reply, List.of(0));
        require(!on(replaced, Ability.WALKSPEED), "Equal replacement consumed stale intent");
        root = replaced;
        reply = prepare(player, 71, false);
        root.set(JustDireDataComponents.FORGE_ENERGY, 25);
        root.setDamageValue(1);
        activate(player, reply, List.of(0));
        require(on(root, Ability.WALKSPEED), "Charging/wear invalidated free passive input");
        reply = prepare(player, 71, false);
        bind(root, List.of(new ToolRecords.AbilityBinding("walkspeed", 72, false, false)));
        activate(player, reply, List.of(0));
        require(on(root, Ability.WALKSPEED), "Changed binding accepted stale intent");
        reply = prepare(player, 72, false);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(5, root);
        activate(player, reply, List.of(0));
        require(on(root, Ability.WALKSPEED), "Moved root consumed stale intent");
        reply = prepare(player, 72, false);
        activate(player, reply, List.of(5, 5));
        require(on(root, Ability.WALKSPEED), "Duplicate slots toggled root");
        reply = prepare(player, 72, false);
        activate(player, reply, List.of(40));
        require(on(root, Ability.WALKSPEED), "Unadmitted slot expanded batch");

        player.containerMenu = new ToolSettingContainer(909, player.getInventory(), player);
        require(prepare(player, 72, false).slots().isEmpty(), "GUI input admitted");
        player.containerMenu = player.inventoryMenu;
        bind(root, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, true)));
        require(JustDireSettings.boundPassives(root, 71, false, false).isEmpty()
                && JustDireSettings.boundPassives(root, 71, false, true).equals(List.of("walkspeed")), "Equipped binding ignored");
        require(prepare(player, 0, true).slots().isEmpty() && prepare(player, 8, true).slots().isEmpty(), "Invalid mouse button admitted");
        var invalid = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        bind(invalid, List.of(new ToolRecords.AbilityBinding("walkspeed", 71, false, false)));
        require(JustDireSettings.boundPassives(invalid, 71, false, false).isEmpty(), "Bare root input accepted");
        var stacked = root.copyWithCount(2);
        require(JustDireSettings.boundPassives(stacked, 71, false, true).isEmpty(), "Count-two root accepted");
        bind(root, List.of(new ToolRecords.AbilityBinding("decoy", 71, false, false)));
        require(JustDireSettings.boundPassives(root, 71, false, true).isEmpty()
                && JustDireSettings.boundAbilities(root, 71, false, true).equals(List.of("decoy")), "Active Decoy was treated as a passive switch");

        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, root);
        player.setItemInHand(InteractionHand.OFF_HAND, inverse);
        require(JustDireHeldToggleCompatibility.target(player) == InteractionHand.MAIN_HAND, "Held priority changed");
        require(!JustDireHeldToggleCompatibility.toggle(player, InteractionHand.OFF_HAND), "Forged opposite hand toggled");
        require(JustDireHeldToggleCompatibility.toggle(player, InteractionHand.MAIN_HAND)
                && root.getOrDefault(JustDireDataComponents.TOOL_ENABLED, false), "Native global callback failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, JustDirePassiveChecks.nativeStack("eclipsealloy"));
        require(JustDireHeldToggleCompatibility.target(player) == null, "Native held priority lost");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(JustDireHeldToggleCompatibility.target(player) == InteractionHand.OFF_HAND, "Offhand-only root missing");
        player.getInventory().clearContent();
        Files.writeString(JustDireConnectedServer.report("input-security.txt"), "Free passive batches, codec, replay/replacement/move/settings/GUI/forgery refusals, equipped mode and held native priority passed\n");
    }

    private static JustDireInputNetwork.InputReply prepare(ServerPlayer player, int key, boolean mouse) {
        preparedKey = key;
        return JustDireInputNetwork.handle(player, new JustDireInputNetwork.InputRequest(++sequence, key, mouse, NONE, List.of()));
    }

    private static void activate(ServerPlayer player, JustDireInputNetwork.InputReply reply, List<Integer> slots) {
        JustDireInputNetwork.handle(player, new JustDireInputNetwork.InputRequest(sequence, preparedKey, false, reply.token(), slots));
    }

    private static JustDireInputNetwork.InputRequest roundtrip(ServerPlayer player, JustDireInputNetwork.InputRequest request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            JustDireInputNetwork.InputRequest.CODEC.encode(buffer, request);
            var decoded = JustDireInputNetwork.InputRequest.CODEC.decode(buffer);
            require(decoded.equals(request), "Input codec changed intent");
            buffer.clear();
            JustDireInputNetwork.InputRequest.CODEC.encode(buffer, request);
            buffer.writeByte(1);
            boolean refused = false;
            try { JustDireInputNetwork.InputRequest.CODEC.decode(buffer); } catch (io.netty.handler.codec.DecoderException expected) { refused = true; }
            require(refused, "Trailing input payload accepted");
            return decoded;
        } finally { buffer.release(); }
    }
}
