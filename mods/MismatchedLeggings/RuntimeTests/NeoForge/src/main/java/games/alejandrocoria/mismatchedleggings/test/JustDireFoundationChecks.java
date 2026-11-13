package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real packaged production roots and paid native menus, without candidate hooks. */
final class JustDireFoundationChecks {
    private static final List<String> NAMES = List.of("walkspeed", "runspeed", "swimspeed", "decoy", "phase");
    private static boolean finished;

    private JustDireFoundationChecks() {
    }

    static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            SkeletonChecks.verify();
            boolean absent = Boolean.getBoolean("leggings.test.jdtAbsent");
            graph();
            var player = RepairFixtures.player(server);
            if (absent) {
                var root = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
                require(energy(root) == null && MixedLeggingsStructure.validData(root).isPresent(), "Optional absence changed ordinary composition");
                player.setItemSlot(EquipmentSlot.LEGS, root);
                CommonHooks.onArmorHurt(player.damageSources().generic(),
                        new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, player);
                require(root.getDamageValue() == 2, "Absent JDT changed ordinary incoming Leggings wear");
            } else {
                creation(player);
                transfers(player);
                preservation(server);
                smithing(server, player);
                if (System.getProperty("leggings.test.jdtFault", "").isEmpty()) {
                    JustDireLifecycleChecks.roots(server);
                }
                malformed();
                if (Boolean.getBoolean("leggings.test.jdtPassives")) {
                    if (!System.getProperty("leggings.test.jdtFault", "").isEmpty()) { JustDirePassiveChecks.fault(player); }
                    else {
                        JustDirePassiveChecks.movement(player, false);
                        JustDirePassiveChecks.phase(player);
                        JustDirePassiveChecks.armor(player);
                        games.alejandrocoria.mismatchedleggings.test.silentgear.JustDireArmorChecks.run(player);
                    }
                }
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "foundation.txt"), absent
                    ? "Quiet optional absence and ordinary packaged composition passed\n"
                    : "Production root union/settings, paid inverse Workbench extraction, conservative FE/capacity, transactions/stale access, zero/absent/dormant recomposition, all 14 paid native upgrade recipes/config refusal/duplicates, tier transformation, typed persistence and malformed inputs passed\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value());
    }

    static void graph() {
        var mods = ModList.get();
        require(mods.isLoaded("justdirethings") != Boolean.getBoolean("leggings.test.jdtAbsent")
                        && mods.isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.jdtBoots")
                        && mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count() == 1,
                "Wrong JDT graph or duplicate nested Things");
    }

    private static void install(ItemStack stack, String name, boolean installed) {
        stack.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), installed);
    }

    private static boolean installed(ItemStack stack, String name) {
        return stack.getOrDefault(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), false);
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    private static EnergyHandler energy(ItemStack root) {
        return ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
    }

    private static void creation(ServerPlayer player) {
        var first = item("ferricore_leggings");
        install(first, "walkspeed", true);
        install(first, "phase", true); // Forged/ineligible donor must not grant Phase.
        first.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 1);
        first.set(JustDireDataComponents.TOOL_ENABLED, false);
        first.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new ToolRecords.AbilityBinding("walkspeed", 86, false, true)));
        var second = item("eclipsealloy_leggings");
        install(second, "runspeed", true);
        second.set(JustDireDataComponents.FORGE_ENERGY, 37);
        second.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 5);
        var a = first.copy();
        var b = second.copy();
        var roots = pair(first, second);
        for (ItemStack root : roots) {
            require(installed(root, "walkspeed") && installed(root, "runspeed") && !installed(root, "phase"), "Root eligible union differs");
            require(energy(root).getCapacityAsLong() == 500000 && energy(root).getAmountAsLong() == 18, "Initial conservative FE differs");
        }
        require(roots.getFirst().get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 1
                && roots.getLast().get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 5
                && !roots.getFirst().get(JustDireDataComponents.TOOL_ENABLED)
                && !roots.getLast().has(JustDireDataComponents.TOOL_ENABLED)
                && roots.getFirst().get(JustDireDataComponents.ABILITY_BINDINGS).size() == 1
                && !roots.getLast().has(JustDireDataComponents.ABILITY_BINDINGS), "Owner controls were averaged/shared");
        require(ItemStack.matches(first, a) && ItemStack.matches(second, b), "Creation edited inputs");
        for (int slot : List.of(2, 3)) {
            player.getInventory().clearContent();
            var menu = new WorkbenchMenu(400, player.getInventory());
            menu.getSlot(0).set(first.copy());
            menu.getSlot(1).set(second.copy());
            for (int repeat = 0; repeat < 3; repeat++) {
                menu.slotsChanged(menu.getSlot(0).container);
                require(ItemStack.matches(menu.getSlot(2).getItem(), roots.getFirst())
                        && ItemStack.matches(menu.getSlot(3).getItem(), roots.getLast()), "Repeated preview changed state");
            }
            menu.clicked(slot, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), roots.get(slot - 2)) && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "Unpaid Workbench output");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, roots.get(slot == 2 ? 1 : 0)), "Companion refund duplicated/lost FE");
        }
        var celestigem = item("celestigem_leggings");
        celestigem.set(JustDireDataComponents.FORGE_ENERGY, 10000);
        for (ItemStack root : pair(celestigem, second)) {
            require(energy(root).getCapacityAsLong() == 500000 && energy(root).getAmountAsLong() == 5018, "Two-provider capacity/charge was divided incorrectly");
        }
        var forged = new ItemStack(Items.DIAMOND_LEGGINGS);
        install(forged, "phase", true);
        forged.set(JustDireDataComponents.FORGE_ENERGY, 500000);
        for (ItemStack root : pair(forged, second)) {
            require(!installed(root, "phase") && energy(root).getAmountAsLong() == 18, "Unrelated source donated forged upgrade/FE");
        }
    }

    private static void transfers(ServerPlayer player) {
        var input = item("eclipsealloy_leggings");
        input.set(JustDireDataComponents.FORGE_ENERGY, 200);
        var roots = pair(input, new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = roots.getFirst();
        var original = root.copy();
        var handler = energy(root);
        try (var transaction = Transaction.openRoot()) {
            require(handler.insert(125, transaction) == 125, "Insertion simulation rejected");
        }
        require(ItemStack.matches(root, original), "Aborted insertion changed root/history");
        try (var transaction = Transaction.openRoot()) {
            require(handler.insert(125, transaction) == 125, "Committed insert quote");
            transaction.commit();
        }
        require(energy(root).getAmountAsLong() == 225 && energy(roots.getLast()).getAmountAsLong() == 100, "Wallets alias or commit did not settle");
        try (var outer = Transaction.openRoot()) {
            try (var nested = Transaction.open(outer)) {
                require(handler.extract(25, nested) == 25, "Nested extraction");
                nested.commit();
            }
        }
        require(energy(root).getAmountAsLong() == 225, "Nested commit escaped outer abort");
        try (var transaction = Transaction.openRoot()) {
            require(handler.extract(1000, transaction) == 225, "Partial extraction quote");
            transaction.commit();
        }
        require(energy(root).getAmountAsLong() == 0 && root.get(LeggingsContent.mixedLeggingsDataType())
                .equals(original.get(LeggingsContent.mixedLeggingsDataType())), "Consumption changed history");
        player.getInventory().setItem(0, root);
        var access = ItemAccess.forPlayerSlot(player, 0);
        var retained = access.getCapability(Capabilities.Energy.ITEM);
        player.getInventory().setItem(0, pair(item("celestigem_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst());
        require(retained.getCapacityAsLong() == 10000, "Retained handler captured old tier");
        player.getInventory().getItem(0).setCount(2);
        try (var transaction = Transaction.openRoot()) {
            require(retained.insert(1, transaction) == 0 && retained.getCapacityAsLong() == 0, "Invalid slot amount accepted FE");
            transaction.commit();
        }
        player.getInventory().setItem(0, new ItemStack(Items.STONE));
        require(retained.getAmountAsLong() == 0 && retained.getCapacityAsLong() == 0, "Retained handler accepted replacement item");
    }

    private static void preservation(MinecraftServer server) {
        var source = item("eclipsealloy_leggings");
        source.set(JustDireDataComponents.FORGE_ENERGY, 500000);
        NAMES.forEach(name -> install(source, name, true));
        var root = pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        root.set(JustDireDataComponents.FORGE_ENERGY, 0);
        install(root, "walkspeed", false);
        root.remove(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.RUNSPEED));
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        root.setDamageValue(7);
        var data = root.get(LeggingsContent.mixedLeggingsDataType());
        var refreshed = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
        require(!installed(refreshed, "walkspeed") && !refreshed.has(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.RUNSPEED))
                && energy(refreshed).getAmountAsLong() == 0 && refreshed.getDamageValue() == 7
                && !refreshed.get(JustDireDataComponents.TOOL_ENABLED), "Recomposition resurrected source flag/wallet/control");
        root.remove(JustDireDataComponents.FORGE_ENERGY);
        refreshed = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
        require(!refreshed.has(JustDireDataComponents.FORGE_ENERGY), "Explicit FE absence became historical refill");
        root.set(JustDireDataComponents.FORGE_ENERGY, 20000);
        refreshed = MixedLeggingsRecomposer.recompose(root, item("celestigem_leggings"), data.right().source());
        require(energy(refreshed).getAmountAsLong() == 10000 && installed(refreshed, "phase"), "Tier capacity clamp lost dormant flag");
        refreshed = MixedLeggingsRecomposer.recompose(refreshed, item("ferricore_leggings"), data.right().source());
        require(energy(refreshed) == null && installed(refreshed, "phase"), "No-provider transformation lost dormant state");
        refreshed = MixedLeggingsRecomposer.recompose(refreshed, source, data.right().source());
        require(energy(refreshed).getAmountAsLong() == 0 && !refreshed.has(JustDireDataComponents.FORGE_ENERGY), "Regaining a provider imported historical energy");
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var decoded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, refreshed).getOrThrow()).getOrThrow();
        require(ItemStack.matches(decoded, refreshed), "New live state failed typed persistence");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, refreshed);
            require(ItemStack.matches(ItemStack.STREAM_CODEC.decode(buffer), refreshed), "Live root/history failed network codec roundtrip");
        } finally {
            buffer.release();
        }
        var equalSource = item("celestigem_leggings");
        equalSource.set(JustDireDataComponents.FORGE_ENERGY, 1000);
        equalSource.set(JustDireDataComponents.TOOL_ENABLED, false);
        equalSource.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 2);
        var equal = pair(equalSource, equalSource.copy()).getFirst();
        var replacement = item("eclipsealloy_leggings");
        replacement.set(JustDireDataComponents.FORGE_ENERGY, 500000);
        replacement.set(JustDireDataComponents.TOOL_ENABLED, true);
        replacement.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 5);
        install(replacement, "phase", true);
        refreshed = MixedLeggingsRecomposer.recompose(equal, replacement, equalSource);
        require(energy(refreshed).getAmountAsLong() == 1000 && !refreshed.get(JustDireDataComponents.TOOL_ENABLED)
                && refreshed.get(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED)) == 2
                && !refreshed.has(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.PHASE)),
                "Equality with an old source projection was mistaken for live ownership");
    }

    private static void smithing(MinecraftServer server, ServerPlayer player) {
        int count = 0;
        for (String tier : List.of("ferricore", "blazegold", "celestigem", "eclipsealloy")) {
            var nativeStack = item(tier + "_leggings");
            var supported = ((com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool) nativeStack.getItem()).getAllAbilities();
            for (String name : NAMES) {
                var addition = item("upgrade_" + name);
                if (!supported.contains(Ability.byName(name))) {
                    var rejected = SmithingChecks.ready(player, ItemStack.EMPTY, pair(nativeStack, new ItemStack(Items.IRON_LEGGINGS)).getFirst(), addition);
                    require(!rejected.getSlot(3).hasItem(), "Ineligible tier installed upgrade " + tier + '/' + name);
                    continue;
                }
                var nativeMenu = SmithingChecks.ready(player, ItemStack.EMPTY, nativeStack.copy(), addition.copy());
                require(installed(nativeMenu.getSlot(3).getItem(), name), "Native recipe positive control missing");
                for (ItemStack root : pair(nativeStack, new ItemStack(Items.IRON_LEGGINGS))) {
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Live root"));
                    root.setDamageValue(3);
                    var original = root.copy();
                    var menu = SmithingChecks.ready(player, ItemStack.EMPTY, root, item("upgrade_" + name));
                    var result = menu.getSlot(3).getItem();
                    require(!result.isEmpty() && installed(result, name), "Native root upgrade missing: " + tier + '/' + name);
                    var expected = original.copy();
                    install(expected, name, true);
                    require(ItemStack.matches(result, expected) && ItemStack.matches(root, original),
                            "Root installation changed more than its flag: " + tier + '/' + name + ' ' + differences(expected, result));
                    menu.createResult();
                    require(ItemStack.matches(menu.getSlot(3).getItem(), expected), "Repeated smithing preview changed result");
                    var recipe = ((ResultContainer) menu.getSlot(3).container).getRecipeUsed().id().identifier();
                    int credits = SmithingCredits.count(player, recipe);
                    var taken = menu.getSlot(3).safeTake(1, 1, player);
                    require(ItemStack.matches(taken, expected) && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem()
                            && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Upgrade payment duplicated or failed");
                    require(SmithingCredits.count(player, recipe) == credits + 1, "Root upgrade recipe credit was lost or duplicated");
                    require(!SmithingChecks.ready(player, ItemStack.EMPTY, taken, addition).getSlot(3).hasItem(), "Duplicate installed on a historical source");
                    var wrong = SmithingChecks.ready(player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), original.copy(), addition);
                    require(!wrong.getSlot(3).hasItem(), "Wrong template charged an upgrade");
                }
                count++;
            }
        }
        require(count == 14, "Pinned native leggings recipe inventory changed");
        var setting = Config.AVAILABLE_ABILITY_MAP.get(Ability.WALKSPEED);
        boolean previous = setting.get();
        try {
            setting.set(false);
            var nativeStack = item("eclipsealloy_leggings");
            var root = pair(nativeStack, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
            require(!SmithingChecks.ready(player, ItemStack.EMPTY, root, item("upgrade_walkspeed")).getSlot(3).hasItem(), "Config-disabled installation passed");
            install(nativeStack, "walkspeed", true);
            require(installed(pair(nativeStack, new ItemStack(Items.IRON_LEGGINGS)).getFirst(), "walkspeed"), "Disabled ability lost stored installation");
        } finally {
            setting.set(previous);
        }
        var root = pair(item("ferricore_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        install(root, "walkspeed", true);
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        var menu = SmithingChecks.ready(player, item("template_blazegold"), root, item("blazegold_ingot"));
        var output = menu.getSlot(3).safeTake(1, 1, player);
        require(!output.isEmpty() && installed(output, "walkspeed") && !output.get(JustDireDataComponents.TOOL_ENABLED)
                && output.get(LeggingsContent.mixedLeggingsDataType()).left().source().is(item("blazegold_leggings").getItem())
                && !menu.getSlot(0).hasItem() && !menu.getSlot(2).hasItem(), "Paid native tier transformation lost live state");
        menu = SmithingChecks.ready(player, item("template_celestigem"), output, item("celestigem"));
        output = menu.getSlot(3).safeTake(1, 1, player);
        require(!output.isEmpty() && energy(output).getAmountAsLong() == 0 && energy(output).getCapacityAsLong() == 10000
                && !menu.getSlot(0).hasItem() && !menu.getSlot(2).hasItem(), "First powered tier created charge or did not pay");
        try (var transaction = Transaction.openRoot()) {
            require(energy(output).insert(777, transaction) == 777, "Live tier fixture charging");
            transaction.commit();
        }
        var binding = List.of(new ToolRecords.AbilityBinding("walkspeed", 5, true, false));
        output.set(JustDireDataComponents.ABILITY_BINDINGS, binding);
        menu = SmithingChecks.ready(player, item("template_eclipsealloy"), output, item("eclipsealloy_ingot"));
        output = menu.getSlot(3).safeTake(1, 1, player);
        require(!output.isEmpty() && energy(output).getAmountAsLong() == 777 && energy(output).getCapacityAsLong() == 500000
                && installed(output, "walkspeed") && !output.get(JustDireDataComponents.TOOL_ENABLED)
                && binding.equals(output.get(JustDireDataComponents.ABILITY_BINDINGS))
                && !output.get(LeggingsContent.mixedLeggingsDataType()).left().source().has(JustDireDataComponents.ABILITY_BINDINGS)
                && !menu.getSlot(0).hasItem() && !menu.getSlot(2).hasItem(), "Paid powered tier upgrade refilled/reset state");
    }

    private static void malformed() {
        for (int charge : new int[]{-1, 500001}) {
            var source = item("eclipsealloy_leggings");
            source.set(JustDireDataComponents.FORGE_ENERGY, charge);
            boolean rejected = false;
            try {
                pair(source, new ItemStack(Items.IRON_LEGGINGS));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "Malformed native charge was accepted");
        }
        var root = pair(item("eclipsealloy_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        root.set(JustDireDataComponents.FORGE_ENERGY, -1);
        require(energy(root) == null, "Negative root charge obtained storage");
        root.remove(LeggingsContent.mixedLeggingsDataType());
        require(energy(root) == null, "Bare/malformed root obtained storage");
    }

    private static String differences(ItemStack expected, ItemStack actual) {
        var types = new java.util.HashSet<>(expected.getComponents().keySet());
        types.addAll(actual.getComponents().keySet());
        return types.stream().filter(type -> !java.util.Objects.equals(expected.get(type), actual.get(type)))
                .map(type -> BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + ": " + expected.get(type) + " -> " + actual.get(type)).toList().toString();
    }
}
