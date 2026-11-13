package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityBinding;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireHeldToggleCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Paid native operations create the live state later saved by the existing recovery harness. */
final class JustDireLifecycleChecks {
    private static int selection;
    private static int sequence;

    private JustDireLifecycleChecks() {}

    static List<ItemStack> roots(MinecraftServer server) throws Exception {
        var player = RepairFixtures.player(server);
        var source = nativeItem("ferricore_leggings");
        source.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED), true);
        source.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 1);
        source.set(JustDireDataComponents.TOOL_ENABLED, false);
        var sourceBefore = source.copy();
        var workbench = new WorkbenchMenu(980, player.getInventory());
        workbench.getSlot(0).set(source.copy());
        workbench.getSlot(1).set(new ItemStack(Items.DIAMOND_LEGGINGS));
        var first = workbench.getSlot(2).getItem().copy();
        workbench.clicked(3, 0, ContainerInput.PICKUP, player);
        var inverse = workbench.getCarried().copy();
        require(!inverse.isEmpty() && !workbench.getSlot(0).hasItem() && !workbench.getSlot(1).hasItem(),
                "Lifecycle Workbench did not pay for inverse extraction");
        workbench.setCarried(ItemStack.EMPTY);
        workbench.removed(player);
        require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, first),
                "Lifecycle Workbench did not return exactly the paid companion");

        var roots = new ArrayList<ItemStack>();
        for (var initial : List.of(first, inverse)) {
            player.getInventory().clearContent();
            var root = initial;
            root.set(DataComponents.CUSTOM_NAME, Component.literal("Live JDT lifecycle " + roots.size()));
            for (String tier : List.of("blazegold", "celestigem", "eclipsealloy")) {
                var history = root.get(LeggingsContent.mixedLeggingsDataType());
                root = take(player, root, nativeItem("template_" + tier),
                        nativeItem(tier.equals("celestigem") ? tier : tier + "_ingot"));
                var changed = root.get(LeggingsContent.mixedLeggingsDataType());
                var oldNative = history.left().source().is(Items.DIAMOND_LEGGINGS) ? history.right().source() : history.left().source();
                var newNative = changed.left().source().is(Items.DIAMOND_LEGGINGS) ? changed.right().source() : changed.left().source();
                require(newNative.is(nativeItem(tier + "_leggings").getItem())
                                && newNative.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 1
                                && !newNative.get(JustDireDataComponents.TOOL_ENABLED)
                                && oldNative.get(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED)),
                        "Native tier transformation lost source history");
                if (tier.equals("celestigem")) {
                    root = take(player, root, ItemStack.EMPTY, nativeItem("upgrade_decoy"));
                    root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 137, true)));
                } else if (tier.equals("eclipsealloy")) {
                    verifyClock(root);
                }
            }
            var history = root.get(LeggingsContent.mixedLeggingsDataType());
            for (String name : List.of("runspeed", "swimspeed", "phase")) {
                root = take(player, root, ItemStack.EMPTY, nativeItem("upgrade_" + name));
                require(root.getOrDefault(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), false)
                                && history.equals(root.get(LeggingsContent.mixedLeggingsDataType())),
                        "Paid root installation edited history or lost its upgrade");
            }

            player.getInventory().setItem(0, root);
            player.containerMenu = new ToolSettingContainer(981, player.getInventory(), player);
            edit(player, JustDireSettingsNetwork.VALUE, "walkspeed", 2, -1, false);
            edit(player, JustDireSettingsNetwork.TOGGLE, "walkspeed", 0, -1, false);
            edit(player, JustDireSettingsNetwork.BINDING, "walkspeed", 2, 4, true);
            edit(player, JustDireSettingsNetwork.VALUE, "runspeed", 3, -1, false);
            edit(player, JustDireSettingsNetwork.TOGGLE, "decoy", 0, -1, false);
            edit(player, JustDireSettingsNetwork.BINDING, "decoy", 2, 72, false);
            player.containerMenu = player.inventoryMenu;
            boolean enabled = root.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true);
            require(JustDireHeldToggleCompatibility.toggle(player, InteractionHand.MAIN_HAND), "Live global toggle refused");
            if (enabled) {
                require(JustDireHeldToggleCompatibility.toggle(player, InteractionHand.MAIN_HAND), "Live global toggle did not reactivate");
            }
            require(root.get(JustDireDataComponents.TOOL_ENABLED), "Live global toggle was not applied");
            try (var transaction = Transaction.openRoot()) {
                require(ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM).insert(88680, transaction) == 88680,
                        "Lifecycle charging did not accept actual FE");
                transaction.commit();
            }
            var beforeWear = root.copy();
            root.hurtAndBreak(7, player, EquipmentSlot.LEGS);
            require(root.getDamageValue() == 7 && root.get(JustDireDataComponents.FORGE_ENERGY) == 88680,
                    "Ordinary mixed wear was replaced by powered immunity/payment");
            var anvil = AnvilRepairChecks.ready(player, root, new ItemStack(Items.DIAMOND));
            var repaired = anvil.getSlot(2).safeTake(1, 1, player);
            require(!repaired.isEmpty() && repaired.getDamageValue() == 0 && !anvil.getSlot(0).hasItem()
                            && !anvil.getSlot(1).hasItem(), "Ordinary material repair failed or was unpaid");
            var expectedRepair = beforeWear.copy();
            expectedRepair.set(DataComponents.REPAIR_COST, repaired.get(DataComponents.REPAIR_COST));
            require(ItemStack.matches(expectedRepair, repaired), "Ordinary repair changed live JDT state/history");
            root = repaired;

            var beforeTrim = root.copy();
            root = take(player, root, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND));
            var trimmed = MixedLeggingsStructure.validData(root).orElseThrow();
            require(trimmed.left().source().has(DataComponents.TRIM) && trimmed.right().source().has(DataComponents.TRIM),
                    "Real trim did not reach both native sources");
            // Trims are an intentional source transformation, but may not touch the live wallet/controls.
            var expected = root.copy();
            expected.set(LeggingsContent.mixedLeggingsDataType(), beforeTrim.get(LeggingsContent.mixedLeggingsDataType()));
            require(ItemStack.matches(expected, beforeTrim), "Trim reset root-owned JDT state");
            verify(root);
            verifyWearer(server, root);
            roots.add(root.copy());
        }
        require(ItemStack.matches(source, sourceBefore), "Paid lifecycle mutated its native input snapshot");
        player.getInventory().clearContent();
        inheritedClocks(player);
        Files.writeString(JustDireConnectedServer.report("jdt-lifecycle.txt"),
                "Paid inverse extraction and both-output Decoy inheritance, tier/upgrade/trim smithing, authoritative settings, charge, wear/repair and live clock/history preservation passed\n");
        return roots;
    }

    static void verify(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var handler = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
        require(handler != null && handler.getCapacityAsLong() == 500000 && handler.getAmountAsLong() == 88680
                        && root.get(JustDireDataComponents.TOOL_ENABLED)
                        && root.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 2
                        && root.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.RUNSPEED)) == 3
                        && !root.get(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.WALKSPEED))
                        && root.get(JustDireDataComponents.ABILITY_BINDINGS).getFirst().isMouse(),
                "Live root settings/FE did not survive lifecycle");
        verifyClock(root);
        require(!root.getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY), true)
                        && root.get(JustDireDataComponents.ABILITY_BINDINGS).contains(new AbilityBinding("decoy", 72, false, false)),
                "Live Decoy controls did not survive lifecycle");
        for (String name : List.of("walkspeed", "runspeed", "swimspeed", "phase", "decoy")) {
            require(root.getOrDefault(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), false),
                    "Lifecycle lost installed " + name);
        }
        var source = data.left().source().is(Items.DIAMOND_LEGGINGS) ? data.right().source() : data.left().source();
        require(source.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 1
                        && !source.get(JustDireDataComponents.TOOL_ENABLED)
                        && !source.has(JustDireDataComponents.FORGE_ENERGY)
                        && !source.has(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.RUNSPEED))
                        && !source.has(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY))
                        && !source.has(JustDireDataComponents.ABILITY_COOLDOWNS)
                        && !source.has(JustDireDataComponents.ABILITY_BINDINGS),
                "Live state was copied into source history");
    }

    private static void verifyClock(ItemStack root) {
        require(root.getOrDefault(JustDireDataComponents.ABILITY_COOLDOWNS, List.of())
                        .equals(List.of(new AbilityCooldown("decoy", 137, true))),
                "Live Decoy clock changed during transformation or stored-world lifecycle");
    }

    private static void inheritedClocks(ServerPlayer player) {
        var celestigem = nativeItem("celestigem_leggings");
        var eclipse = nativeItem("eclipsealloy_leggings");
        celestigem.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        eclipse.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        // Effective Eclipse parameters make active-137 a 1,337-tick restriction, longer than inactive-1,100.
        celestigem.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 137, true)));
        eclipse.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1100, false)));
        var leftBefore = celestigem.copy();
        var rightBefore = eclipse.copy();
        for (int output : List.of(2, 3)) {
            player.getInventory().clearContent();
            var menu = new WorkbenchMenu(982, player.getInventory());
            menu.getSlot(0).set(celestigem.copy());
            menu.getSlot(1).set(eclipse.copy());
            var companion = menu.getSlot(output == 2 ? 3 : 2).getItem().copy();
            menu.clicked(output, 0, ContainerInput.PICKUP, player);
            var taken = menu.getCarried().copy();
            require(!taken.isEmpty() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(),
                    "Inherited Decoy extraction was unpaid");
            verifyClock(taken);
            verifyClock(companion);
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, companion),
                    "Inherited Decoy companion was lost or duplicated");
        }
        require(ItemStack.matches(celestigem, leftBefore) && ItemStack.matches(eclipse, rightBefore),
                "Inherited Decoy extraction mutated native inputs");
        player.getInventory().clearContent();
    }

    static void verifyWearer(MinecraftServer server, ItemStack saved) {
        var player = RepairFixtures.player(server);
        var root = saved.copy();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        JustDirePassiveChecks.close(player.getAttributeValue(JDTRegistration.PHASE), 1, "Loaded live Phase did not reactivate");
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        JustDirePassiveChecks.close(player.getAttributeValue(JDTRegistration.PHASE), 0, "Loaded root ignored its live global control");
    }

    private static void edit(ServerPlayer player, int operation, String name, int value, int key, boolean mouse) {
        int id = ++selection;
        var select = new JustDireSettingsNetwork.SettingsRequest(player.containerMenu.containerId, 0, id, ++sequence,
                JustDireSettingsNetwork.NONE, JustDireSettingsNetwork.SELECT, "", 0, -1, false, true);
        var reply = JustDireSettingsNetwork.edit(player, select);
        require(reply.accepted(), "Lifecycle native settings selection refused");
        require(JustDireSettingsNetwork.edit(player, new JustDireSettingsNetwork.SettingsRequest(player.containerMenu.containerId,
                0, id, ++sequence, reply.token(), operation, name, value, key, mouse, false)).accepted(),
                "Lifecycle live settings edit refused");
    }

    private static ItemStack take(ServerPlayer player, ItemStack root, ItemStack template, ItemStack addition) {
        var menu = SmithingChecks.ready(player, template, root, addition);
        var result = menu.getSlot(3).safeTake(1, 1, player);
        require(!result.isEmpty() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem()
                        && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Lifecycle smithing payment/result differs");
        return result;
    }

    private static ItemStack nativeItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", path)));
    }
}
