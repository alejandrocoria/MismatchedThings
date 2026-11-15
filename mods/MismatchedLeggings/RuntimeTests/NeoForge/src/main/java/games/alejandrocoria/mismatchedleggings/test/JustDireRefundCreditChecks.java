package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireRefundCredits;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.refunds.RefundMutations;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Conserved purchases produced by real factories and paid menus, exercised through actual durability wear. */
final class JustDireRefundCreditChecks {
    private static final List<String> NAMES = List.of("walkspeed", "runspeed", "swimspeed", "decoy", "phase");
    private static final int LEGS = EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE);
    private static Runnable onDrop;
    private static boolean cancelDrop;
    private static int callbacks;

    private JustDireRefundCreditChecks() {
    }

    static void run(MinecraftServer server, StringBuilder report) {
        creation(server);
        JustDireFoundationChecks.smithing(server, RepairFixtures.player(server));
        preservation(server);
        locations(server);
        if (System.getProperty("leggings.test.refundFault", "").equals("receipts")) {
            receipts(server);
            report.append("Foreign consumption controls: skipped/partial shrink, replacement/move, component/list edits, restored count, ")
                    .append("throwing consumption, reentry before consumption and cancelled/throwing/reentrant delivery passed without retries\n");
        }
        report.append("Leggings purchases: canonical owner/inverse imports, permanent tier eligibility, all 14 native recipes, paid-only result ownership, ")
                .append("recomposition/tier and typed network preservation; real supported-slot wear, invalid-state and Unbreaking controls passed\n");
    }

    static void delivery(EntityJoinLevelEvent event) {
        if (cancelDrop) {
            event.setCanceled(true);
        }
        var action = onDrop;
        onDrop = null;
        if (action != null) {
            action.run();
        }
    }

    static void reopened(MinecraftServer server, ItemStack stored) {
        var credits = stored.get(JustDireRefundCompatibility.component());
        var expected = credits == null ? List.<String>of() : credits.names();
        var root = stored.copy();
        if (root.has(JustDireDataComponents.FORGE_ENERGY)) {
            root.set(JustDireDataComponents.FORGE_ENERGY, 0);
        }
        var player = equip(server, root, LEGS);
        try {
            wear(player, root);
            require(root.isEmpty() && callbacks == 1, "Recovered root did not really break exactly once");
            returned(expected.toArray(String[]::new));
        } finally {
            JustDireRefundChecks.observed = null;
        }
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value());
    }

    private static ItemStack nativeInput(String tier, String... names) {
        var input = item(tier + "_leggings");
        for (String name : names) {
            input.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), true);
        }
        return input;
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    private static ItemStack mixed() {
        return pair(nativeInput("ferricore", "walkspeed"), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
    }

    private static void credits(ItemStack root, String... expected) {
        var value = root.get(JustDireRefundCompatibility.component());
        require((value == null ? List.<String>of() : value.names()).equals(List.of(expected)), "Wrong purchase owner: " + value);
    }

    private static void creation(MinecraftServer server) {
        for (String tier : List.of("ferricore", "blazegold", "celestigem", "eclipsealloy")) {
            for (String name : NAMES) {
                var source = nativeInput(tier, name);
                var before = source.copy();
                boolean eligible = ((ToggleableTool) source.getItem()).getAllAbilities().contains(Ability.byName(name));
                var roots = pair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
                credits(roots.getFirst(), eligible ? new String[]{name} : new String[0]);
                credits(roots.getLast());
                require(ItemStack.matches(source, before), "Credit import mutated source history");
                for (int orientation = 0; orientation < 2; orientation++) {
                    var root = roots.get(orientation);
                    var player = equip(server, root, LEGS);
                    wear(player, root);
                    require(root.isEmpty() && callbacks == 1, "Native-family break control failed");
                    returned(eligible && orientation == 0 ? new String[]{name} : new String[0]);
                }
            }
        }
        for (var root : pair(nativeInput("ferricore", "walkspeed"), nativeInput("blazegold", "walkspeed"))) {
            credits(root, "walkspeed");
            var player = equip(server, root, LEGS);
            wear(player, root);
            returned("walkspeed");
        }
        var disjoint = pair(nativeInput("ferricore", "walkspeed"), nativeInput("eclipsealloy", "phase"));
        credits(disjoint.getFirst(), "walkspeed");
        credits(disjoint.getLast(), "phase");
        for (int index = 0; index < 2; index++) {
            var root = disjoint.get(index);
            var player = equip(server, root, LEGS);
            wear(player, root);
            returned(index == 0 ? "walkspeed" : "phase");
        }
        // A higher-tier other source must not legitimize a forged owner flag or own credit.
        var forged = nativeInput("ferricore", "phase");
        forged.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("phase")));
        var roots = pair(forged, nativeInput("eclipsealloy", "phase"));
        credits(roots.getFirst());
        credits(roots.getLast(), "phase");
        credits(pair(nativeInput("blazegold"), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst());

        var setting = Config.AVAILABLE_ABILITY_MAP.get(Ability.WALKSPEED);
        boolean enabled = setting.get();
        try {
            setting.set(false);
            credits(mixed(), "walkspeed");
        } finally {
            setting.set(enabled);
        }
    }

    private static void preservation(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var roots = pair(nativeInput("ferricore", "walkspeed"), new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = roots.getFirst();
        var companion = roots.getLast().copy();
        var history = root.get(LeggingsContent.mixedLeggingsDataType());
        root = paid(player, root, ItemStack.EMPTY, item("upgrade_runspeed"));
        require(ItemStack.matches(roots.getLast(), companion), "Selected-result payment changed the companion");
        credits(root, "walkspeed", "runspeed");
        require(history.equals(root.get(LeggingsContent.mixedLeggingsDataType())), "Paid credit changed history");
        var refreshed = MixedLeggingsRecomposer.recompose(root, history.left().source(), history.right().source());
        credits(refreshed, "walkspeed", "runspeed");
        root = paid(player, refreshed, item("template_blazegold"), item("blazegold_ingot"));
        credits(root, "walkspeed", "runspeed");
        root = paid(player, root, item("template_celestigem"), item("celestigem"));
        root = paid(player, root, ItemStack.EMPTY, item("upgrade_decoy"));
        credits(root, "walkspeed", "runspeed", "decoy");
        root = paid(player, root, item("template_eclipsealloy"), item("eclipsealloy_ingot"));
        credits(root, "walkspeed", "runspeed", "decoy");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            var synced = ItemStack.STREAM_CODEC.decode(buffer);
            require(ItemStack.matches(root, synced), "Typed root synchronization changed purchases/state");
        } finally {
            buffer.release();
        }
        var type = JustDireRefundCompatibility.component();
        for (boolean empty : List.of(false, true)) {
            var legacy = mixed();
            legacy.remove(type);
            if (empty) {
                legacy.set(type, new JustDireRefundCredits(List.of()));
            }
            var data = legacy.get(LeggingsContent.mixedLeggingsDataType());
            var fresh = MixedLeggingsRecomposer.recompose(legacy, data.left().source(), data.right().source());
            require(fresh.has(type) == empty, "Recomposition backfilled absent/empty credit from history");
            credits(fresh);
            var owner = equip(server, fresh, LEGS);
            wear(owner, fresh);
            returned();
        }
        // Provider removal makes a paid higher-tier purchase dormant; it must remain refundable.
        var dormant = pair(nativeInput("eclipsealloy", "phase"), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        var data = dormant.get(LeggingsContent.mixedLeggingsDataType());
        dormant = MixedLeggingsRecomposer.recompose(dormant, nativeInput("ferricore"), data.right().source());
        credits(dormant, "phase");
        var owner = equip(server, dormant, LEGS);
        wear(owner, dormant);
        returned("phase");
    }

    private static ItemStack paid(ServerPlayer player, ItemStack root, ItemStack template, ItemStack addition) {
        var before = root.copy();
        var history = root.get(LeggingsContent.mixedLeggingsDataType());
        var menu = SmithingChecks.ready(player, template, root, addition);
        require(menu.getSlot(3).hasItem() && ItemStack.matches(root, before), "Missing/impure paid preview");
        menu.createResult();
        var result = menu.getSlot(3).safeTake(1, 1, player);
        require(!result.isEmpty() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem()
                && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Purchase extraction did not pay exactly once");
        require(history.equals(before.get(LeggingsContent.mixedLeggingsDataType())), "Preview mutated original history");
        return result;
    }

    private static ServerPlayer equip(MinecraftServer server, ItemStack root, int slot) {
        var player = RepairFixtures.player(server);
        player.getInventory().setItem(slot, root);
        root.setDamageValue(root.getMaxDamage() - 1);
        begin(root);
        return player;
    }

    private static void begin(ItemStack root) {
        JustDireRefundChecks.observed = root;
        JustDireRefundChecks.jdtDrops = 0;
        JustDireRefundChecks.returnedUpgrades.clear();
        callbacks = 0;
        RefundMutations.reset();
        onDrop = null;
        cancelDrop = false;
    }

    private static void wear(ServerPlayer player, ItemStack root) {
        root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
    }

    private static void returned(String... names) {
        require(JustDireRefundChecks.jdtDrops == names.length
                        && JustDireRefundChecks.returnedUpgrades.stream().sorted().toList().equals(List.of(names).stream().sorted().toList()),
                "Wrong actual returned purchases: " + JustDireRefundChecks.returnedUpgrades);
    }

    private static void locations(MinecraftServer server) {
        for (int slot : new int[]{LEGS, 0, 17, Inventory.SLOT_OFFHAND}) {
            var root = mixed();
            var player = equip(server, root, slot);
            player.containerMenu = ChestMenu.threeRows(927, player.getInventory());
            wear(player, root);
            require(root.isEmpty() && callbacks == 1, "Real supported-slot wear/callback changed");
            returned("walkspeed");
        }
        for (String control : List.of("copy", "detached", "feet", "creative", "spectator", "malformed", "flag", "manual", "unbreaking", "disabled")) {
            var root = mixed();
            var player = equip(server, root, LEGS);
            switch (control) {
                case "copy" -> player.setItemSlot(EquipmentSlot.LEGS, root.copy());
                case "detached" -> player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                case "feet" -> {
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.FEET, root);
                }
                case "creative" -> player.setGameMode(GameType.CREATIVE);
                case "spectator" -> player.setGameMode(GameType.SPECTATOR);
                case "malformed" -> root.remove(LeggingsContent.mixedLeggingsDataType());
                case "flag" -> root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED), false);
                case "disabled" -> {
                    root.set(JustDireDataComponents.TOOL_ENABLED, false);
                    root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.WALKSPEED), false);
                }
            }
            if (control.equals("manual")) {
                root.shrink(1);
            } else if (control.equals("unbreaking")) {
                int survived = 0;
                for (int attempt = 0; attempt < 24; attempt++) {
                    var enchanted = mixed();
                    enchanted.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
                    var wearer = equip(server, enchanted, LEGS);
                    CommonHooks.onArmorHurt(wearer.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, wearer);
                    if (!enchanted.isEmpty()) {
                        survived++;
                        returned();
                    } else {
                        returned("walkspeed");
                    }
                }
                require(survived > 0, "Unbreaking survival was not exercised");
            } else {
                var setting = Config.AVAILABLE_ABILITY_MAP.get(Ability.WALKSPEED);
                boolean enabled = setting.get();
                try {
                    if (control.equals("disabled")) {
                        setting.set(false);
                    }
                    wear(player, root);
                } finally {
                    setting.set(enabled);
                }
            }
            if (!control.equals("unbreaking")) {
                returned(control.equals("disabled") ? new String[]{"walkspeed"} : new String[0]);
            }
        }
    }

    private static void receipts(MinecraftServer server) {
        for (String fault : List.of("skip", "partial", "replace", "move", "edit", "list", "refill", "throw", "reenter", "dropReenter", "dropCancel", "dropThrow")) {
            var root = mixed();
            var player = equip(server, root, LEGS);
            RefundMutations.root = root;
            var mutable = new ArrayList<>(List.of(new ToolRecords.AbilityBinding("walkspeed", 86, false, true)));
            root.set(JustDireDataComponents.ABILITY_BINDINGS, mutable);
            switch (fault) {
                case "skip" -> RefundMutations.skip = true;
                case "partial" -> RefundMutations.before = () -> root.setCount(2);
                case "replace" -> RefundMutations.after = () -> player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                case "move" -> RefundMutations.before = () -> {
                    player.getInventory().setItem(2, root);
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                };
                case "edit" -> RefundMutations.after = () -> root.set(DataComponents.CUSTOM_NAME, Component.literal("Foreign edit"));
                case "list" -> RefundMutations.after = mutable::clear;
                case "refill" -> RefundMutations.after = () -> root.setCount(1);
                case "throw" -> RefundMutations.after = () -> { throw new IllegalStateException("controlled consumption"); };
                case "reenter" -> RefundMutations.before = () -> wear(player, root);
                case "dropReenter" -> onDrop = () -> {
                    root.setCount(1);
                    wear(player, root);
                };
                case "dropCancel" -> cancelDrop = true;
                case "dropThrow" -> onDrop = () -> { throw new IllegalStateException("controlled delivery"); };
            }
            boolean threw = false;
            try {
                wear(player, root);
            } catch (IllegalStateException failure) {
                require(failure.getMessage().equals(fault.equals("throw") ? "controlled consumption" : "controlled delivery"), "Unexpected wear exception");
                threw = true;
            } finally {
                RefundMutations.reset();
                onDrop = null;
                cancelDrop = false;
            }
            require(threw == (fault.equals("throw") || fault.equals("dropThrow")), "Foreign exception swallowed/retried");
            boolean refund = fault.equals("reenter") || fault.startsWith("drop");
            returned(refund ? new String[]{"walkspeed"} : new String[0]);
            require(callbacks == (threw ? 0 : fault.equals("reenter") || fault.equals("dropReenter") ? 2 : 1), "Wrong ordinary break callback count: " + fault);
            if (fault.equals("replace")) {
                require(player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS), "Receipt refusal overwrote replacement");
            }
        }
    }
}
