package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.SettingsRequest;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.BINDING;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.NONE;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.PARTICLES;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.SELECT;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.SettingsReply;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.TOGGLE;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.VALUE;
import static games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork.edit;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual native menu, production admission and network codecs, not a second settings implementation. */
final class JustDireSettingsChecks {
    private static int selection;
    private static int sequence;

    private JustDireSettingsChecks() {}

    static void run(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        var menu = new ToolSettingContainer(901, player.getInventory(), player);
        player.containerMenu = menu; // Empty-hand construction must also work without Boots.
        var source = JustDirePassiveChecks.nativeStack("eclipsealloy");
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = pair.primary();
        player.getInventory().setItem(0, root);
        var snapshot = root.copy();
        require(JustDireSettings.metadata(root, "walkspeed").maximum() > 1 && JustDireSettings.metadata(root, "phase").minimum() == -1,
                "Native profile or Phase toggle-only metadata missing");

        var selected = select(player, 0);
        require(selected.accepted(), "Valid root selection refused");
        root.set(JustDireDataComponents.FORGE_ENERGY, 777);
        root.set(DataComponents.DAMAGE, 1);
        var edit = request(player, 0, selected.token(), VALUE, "walkspeed", 3, -1, false, true);
        require(edit(player, roundtrip(player, edit)).accepted(), "Charging/wear invalidated selected root");
        require(root.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 3, "Valid slider edit lost");
        require(!edit(player, edit).accepted(), "Replayed request changed a root");
        var after = root.copy();
        require(!edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted()
                && ItemStack.matches(root, after), "Replay invalidation left a usable token");

        for (var bad : List.of(
                new Control(VALUE, "walkspeed", 999, -1, false), new Control(VALUE, "phase", 1, -1, false),
                new Control(TOGGLE, "decoy", 0, -1, false), new Control(TOGGLE, "lavarepair", 0, -1, false),
                new Control(BINDING, "walkspeed", 1, 71, false), new Control(BINDING, "walkspeed", 2, 0, true),
                new Control(BINDING, "walkspeed", 2, 8, true), new Control(BINDING, "walkspeed", 2, 999, false),
                new Control(PARTICLES, "walkspeed", 0, -1, false), new Control(99, "walkspeed", 0, -1, false))) {
            selected = select(player, 0);
            after = root.copy();
            require(!edit(player, request(player, 0, selected.token(), bad.operation, bad.name, bad.value, bad.key, bad.mouse, true)).accepted()
                    && ItemStack.matches(root, after), "Unsupported/malformed edit mutated state: " + bad);
        }

        selected = select(player, 0);
        require(edit(player, request(player, 0, selected.token(), BINDING, "walkspeed", 2, 4, true, false)).accepted(), "Valid mouse binding refused");
        var records = root.get(JustDireDataComponents.ABILITY_BINDINGS);
        require(records.size() == 1 && records.getFirst().isMouse() && !records.getFirst().requireEquipped(), "Native binding/location not stored");
        var replyBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            SettingsReply.CODEC.encode(replyBuffer, selected);
            require(SettingsReply.CODEC.decode(replyBuffer).equals(selected), "Reply codec mismatch");
        } finally { replyBuffer.release(); }

        selected = select(player, 0);
        after = root.copy();
        player.getInventory().setItem(0, root.copy());
        require(!edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted()
                && ItemStack.matches(player.getInventory().getItem(0), after), "Equal replacement inherited old token");
        player.getInventory().setItem(0, root);
        selected = select(player, 0);
        player.getInventory().setItem(1, root);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        require(!edit(player, request(player, 1, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted(), "Moved pair followed token");
        player.getInventory().setItem(0, root);
        player.getInventory().setItem(1, ItemStack.EMPTY);

        selected = select(player, 0);
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        require(!edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted(), "External settings change retained token");
        selected = select(player, 0);
        player.containerMenu = player.inventoryMenu;
        require(!edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted(), "Closed/wrong menu admitted edit");
        player.containerMenu = menu;
        selected = select(player, 0);
        player.containerMenu = new ToolSettingContainer(menu.containerId, player.getInventory(), player);
        require(!edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted(), "New menu reused old token");
        player.containerMenu = menu;

        for (var invalid : List.of(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(root.getItem()), root.copyWithCount(2))) {
            player.getInventory().setItem(0, invalid);
            require(!select(player, 0).accepted(), "Invalid/count/wrong-product target admitted");
        }
        var ordinary = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        ordinary.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.PHASE), true);
        player.getInventory().setItem(0, ordinary);
        require(!select(player, 0).accepted(), "Forged native flag granted source entitlement");
        var lowTier = MixedLeggingsFactory.createPair(JustDirePassiveChecks.nativeStack("ferricore"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        lowTier.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.PHASE), true);
        player.getInventory().setItem(0, lowTier);
        selected = select(player, 0);
        require(selected.accepted() && !edit(player, request(player, 0, selected.token(), TOGGLE, "phase", 0, -1, false, true)).accepted(),
                "Native low tier gained Phase settings through forged root flag");
        player.getInventory().setItem(0, pair.inverse());
        require(select(player, 0).accepted(), "Inverse root not editable");
        player.getInventory().setItem(EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE), root);
        require(!select(player, EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE)).accepted(), "Wrong equipment slot admitted");
        for (int slot : List.of(EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE), Inventory.SLOT_OFFHAND)) {
            player.getInventory().setItem(slot, root);
            require(select(player, slot).accepted(), "Supported equipment/offhand slot refused");
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }

        player.getInventory().setItem(0, root);
        root.remove(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED));
        selected = select(player, 0);
        require(!edit(player, request(player, 0, selected.token(), VALUE, "walkspeed", 2, -1, false, true)).accepted(), "Missing installation admitted edit");
        root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED), true);
        var config = Config.AVAILABLE_ABILITY_MAP.get(Ability.WALKSPEED);
        boolean configured = config.get();
        try {
            selected = select(player, 0);
            config.set(false);
            require(!edit(player, request(player, 0, selected.token(), VALUE, "walkspeed", 2, -1, false, true)).accepted(), "Disabled native ability admitted edit");
        } finally { config.set(configured); }
        require(ItemStack.matches(snapshot.get(games.alejandrocoria.mismatchedleggings.content.LeggingsContent.mixedLeggingsDataType()).left().source(),
                root.get(games.alejandrocoria.mismatchedleggings.content.LeggingsContent.mixedLeggingsDataType()).left().source()), "Settings changed source history");
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        Files.writeString(JustDireConnectedServer.report("settings-security.txt"), "Native empty menu, live root edits, token/replay/slot/menu/identity/config/installation refusals and bounded codecs passed\n");
    }

    private static SettingsReply select(ServerPlayer player, int slot) {
        selection++;
        return edit(player, request(player, slot, NONE, SELECT, "", 0, -1, false, true));
    }

    private static SettingsRequest request(ServerPlayer player, int slot, UUID token, int operation, String ability,
                                            int value, int key, boolean mouse, boolean equipped) {
        return new SettingsRequest(player.containerMenu.containerId, slot, selection, ++sequence, token, operation, ability, value, key, mouse, equipped);
    }

    private static SettingsRequest roundtrip(ServerPlayer player, SettingsRequest request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            SettingsRequest.CODEC.encode(buffer, request);
            var decoded = SettingsRequest.CODEC.decode(buffer);
            require(decoded.equals(request), "Settings request codec mismatch");
            buffer.clear();
            SettingsRequest.CODEC.encode(buffer, request);
            buffer.writeByte(1);
            try { SettingsRequest.CODEC.decode(buffer); throw new AssertionError("Trailing settings bytes admitted"); }
            catch (io.netty.handler.codec.DecoderException expected) { /* Bounded payload refused before handler. */ }
            return decoded;
        } finally { buffer.release(); }
    }

    private record Control(int operation, String name, int value, int key, boolean mouse) {}
}
