package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.mixin.GrindstoneXpInvoker;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class GrindstoneChecks {
    static final BlockPos STATION = new BlockPos(20, 65, 20);
    static Consumer<MinecraftServer> nativeEligibilityCheck = server -> {};
    private static boolean prepared;

    private GrindstoneChecks() {
    }

    static void prepare(MinecraftServer server) {
        if (!prepared) {
            // Entity visibility follows chunk loading on later ticks, not the getChunk call itself.
            server.overworld().setChunkForced(STATION.getX() >> 4, STATION.getZ() >> 4, true);
            server.overworld().getChunkAt(STATION);
            prepared = true;
        }
    }

    static void verify(MinecraftServer server) {
        clearOrbs(server.overworld());
        ExperienceOrb.award(server.overworld(), Vec3.atCenterOf(STATION), 1);
        require(xp(server.overworld()) == 1, "Native XP control is not visible in the loaded fixture chunk");
        clearOrbs(server.overworld());
        singles(server);
        donors(server);
        experience(server);
        rejectedInputs(server);
        controls(server);
        nativeEligibilityCheck.accept(server);
    }

    private static void singles(MinecraftServer server) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var curse = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
        for (boolean inverse : List.of(false, true)) {
            for (int slot : List.of(0, 1)) {
                var player = RepairFixtures.player(server);
                var root = RepairFixtures.mixed(inverse);
                root.enchant(protection, 3);
                root.enchant(curse, 1);
                var original = root.copy();
                var menu = ready(player, slot == 0 ? root : ItemStack.EMPTY, slot == 1 ? root : ItemStack.EMPTY);
                var result = menu.getSlot(2).getItem();
                RepairFixtures.preserved(original, result);
                require(result.getDamageValue() == original.getDamageValue()
                                && original.get(DataComponents.CUSTOM_NAME).equals(result.get(DataComponents.CUSTOM_NAME))
                                && EnchantmentHelper.getItemEnchantmentLevel(protection, result) == 0
                                && EnchantmentHelper.getItemEnchantmentLevel(curse, result) == 1
                                && result.getOrDefault(DataComponents.REPAIR_COST, 0) == 1,
                        "Single-input grinding changed durability/name or curse/prior-work rules");
                require(ItemStack.matches(original, root) && xp(server.overworld()) == 0,
                        "Grindstone preview mutated the root or awarded XP");
                take(server, player, menu, slot == 1, protection.value().getMinCost(3));

                var cancelled = ready(player, slot == 0 ? original.copy() : ItemStack.EMPTY,
                        slot == 1 ? original.copy() : ItemStack.EMPTY);
                player.getInventory().clearContent();
                cancelled.removed(player);
                require(xp(server.overworld()) == 0 && WorkbenchChecks.contains(player, original)
                                && WorkbenchChecks.inventory(player).size() == 1,
                        "Closing a single-input preview paid XP or lost the root");
            }
        }
        var player = RepairFixtures.player(server);
        require(!ready(player, RepairFixtures.mixed(false), ItemStack.EMPTY).getSlot(2).hasItem(),
                "Plain unenchanted mixed root gained a disenchantment result");
        var onlyCurse = RepairFixtures.mixed(false);
        onlyCurse.enchant(curse, 1);
        take(server, player, ready(player, ItemStack.EMPTY, onlyCurse), false, 0);
    }

    private static void donors(MinecraftServer server) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var vanishing = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
        var binding = enchantments.getOrThrow(Enchantments.BINDING_CURSE);
        for (boolean inverse : List.of(false, true)) {
            for (boolean reverse : List.of(false, true)) {
                for (var item : List.of(Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS)) {
                    var player = RepairFixtures.player(server);
                    var root = RepairFixtures.mixed(inverse);
                    root.enchant(vanishing, 1);
                    var original = root.copy();
                    var donor = new ItemStack(item);
                    // Force the native merge's larger-capacity path; it must not enlarge the root.
                    donor.set(DataComponents.MAX_DAMAGE, 1000);
                    donor.setDamageValue(990);
                    donor.enchant(protection, 1);
                    donor.enchant(vanishing, 2);
                    donor.enchant(binding, 1);
                    var donorBefore = donor.copy();
                    var menu = ready(player, reverse ? donor : root, reverse ? root : donor);
                    var result = menu.getSlot(2).getItem();
                    RepairFixtures.preserved(original, result);
                    int expectedDamage = Math.max(0, root.getDamageValue() - 10 - root.getMaxDamage() * 5 / 100);
                    require(result.getDamageValue() == expectedDamage
                                    && result.get(DataComponents.CUSTOM_NAME).equals(original.get(DataComponents.CUSTOM_NAME))
                                    && EnchantmentHelper.getItemEnchantmentLevel(protection, result) == 0
                                    && EnchantmentHelper.getItemEnchantmentLevel(vanishing, result) == 1
                                    && EnchantmentHelper.getItemEnchantmentLevel(binding, result) == 1
                                    && result.getOrDefault(DataComponents.REPAIR_COST, 0) == 3,
                            "Donor grinding changed root priority, native curses or prior-work calculation");
                    require(ItemStack.matches(original, root) && ItemStack.matches(donorBefore, donor), "Donor preview changed inputs");
                    take(server, player, menu, reverse, 1);
                }
            }
        }
        var player = RepairFixtures.player(server);
        var root = RepairFixtures.mixed(false);
        var saturated = ready(player, root, new ItemStack(Items.IRON_LEGGINGS));
        require(saturated.getSlot(2).getItem().getDamageValue() == 0, "Donor repair failed to saturate at root capacity");
        take(server, player, saturated, false, 0);
        var cancelled = ready(player, RepairFixtures.mixed(false), new ItemStack(Items.IRON_LEGGINGS));
        player.getInventory().clearContent();
        cancelled.removed(player);
        require(xp(server.overworld()) == 0 && WorkbenchChecks.inventory(player).size() == 2,
                "Closing donor repair paid XP or lost an input");
    }

    private static void experience(MinecraftServer server) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
        var left = new ItemStack(Items.IRON_LEGGINGS);
        var right = new ItemStack(Items.GOLDEN_LEGGINGS);
        left.enchant(protection, 1);
        right.enchant(protection, 1);
        var pair = MixedLeggingsFactory.createPair(left, right, CompositionRules.defaults());
        var player = RepairFixtures.player(server);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(EnchantmentHelper.getItemEnchantmentLevel(protection, root) == 2, "Nonlinear XP fixture lacks merged level II");
            var menu = ready(player, root.copy(), ItemStack.EMPTY);
            require(potential(menu, root) == 1, "Nonlinear merged cost escaped the original source XP budget");
            take(server, player, menu, false, 1);

            var later = root.copy();
            later.enchant(unbreaking, 3);
            int paidLater = unbreaking.value().getMinCost(3);
            var donor = new ItemStack(Items.IRON_LEGGINGS);
            donor.enchant(protection, 1);
            var withDonor = ready(player, donor, later);
            require(potential(withDonor, later) == 1 + paidLater && potential(withDonor, donor) == 1,
                    "Later independent root value or ordinary donor XP was discounted");
            take(server, player, withDonor, true, 2 + paidLater);

            var stripped = root.copy();
            stripped.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            require(potential(menu, stripped) == 0, "Stripped root reclaimed its historical baseline");
        }
        // An odd one-sided baseline must round down before the native random payout.
        right.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var odd = MixedLeggingsFactory.createPair(left, right, CompositionRules.defaults()).primary();
        var noReward = ready(player, odd, ItemStack.EMPTY);
        require(potential(noReward, odd) == 0, "Odd duplicated potential rounded up");
        take(server, player, noReward, false, 0);
    }

    private static void rejectedInputs(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var root = RepairFixtures.mixed(false);
        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        root.enchant(protection, 1);
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        bare.set(DataComponents.ENCHANTMENTS, root.get(DataComponents.ENCHANTMENTS));
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        for (var inputs : List.of(List.of(root, RepairFixtures.mixed(true)), List.of(root, new ItemStack(Items.DIAMOND_LEGGINGS)),
                List.of(bare, ItemStack.EMPTY), List.of(malformed, ItemStack.EMPTY), List.of(root.copyWithCount(2), ItemStack.EMPTY))) {
            for (boolean reverse : List.of(false, true)) {
                var first = inputs.get(reverse ? 1 : 0).copy();
                var second = inputs.get(reverse ? 0 : 1).copy();
                var menu = ready(player, first, second);
                require(!menu.getSlot(2).hasItem() && menu.quickMoveStack(player, 2).isEmpty()
                                && xp(server.overworld()) == 0, "Invalid grindstone inputs produced output or XP");
            }
        }
        require(potential(ready(player, ItemStack.EMPTY, ItemStack.EMPTY), bare) == 0, "Invalid root has XP potential");
    }

    private static void controls(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        var ordinary = new ItemStack(Items.IRON_LEGGINGS);
        ordinary.enchant(protection, 1);
        var menu = ready(player, ordinary, ItemStack.EMPTY);
        require(menu.getSlot(2).getItem().is(Items.IRON_LEGGINGS) && potential(menu, ordinary) == 1,
                "Native ordinary disenchantment/XP was intercepted");
        take(server, player, menu, false, 1);
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.mixed();
            boots.enchant(protection, 1);
            var donor = new ItemStack(Items.GOLDEN_BOOTS);
            var control = ready(player, donor, boots);
            require(control.getSlot(2).getItem().getItem() == boots.getItem() && potential(control, boots) == 1,
                    "Leggings interfered with Boots' grindstone repair/XP hooks");
            take(server, player, control, true, 1);
            require(!ready(player, RepairFixtures.mixed(false), boots).getSlot(2).hasItem(), "Cross-product repair admitted");
        }
    }

    static GrindstoneMenu ready(ServerPlayer player, ItemStack first, ItemStack second) {
        int firstCount = first.getCount();
        int secondCount = second.getCount();
        var menu = new GrindstoneMenu(81, player.getInventory(), ContainerLevelAccess.create(player.level(), STATION));
        menu.getSlot(0).set(first);
        first.setCount(firstCount);
        menu.getSlot(1).set(second);
        second.setCount(secondCount);
        // Force a fresh native recomputation after preserving malformed-count controls.
        menu.slotsChanged(menu.getSlot(0).container);
        return menu;
    }

    private static int potential(GrindstoneMenu menu, ItemStack input) {
        return ((GrindstoneXpInvoker) menu.getSlot(2)).leggings$potential(input);
    }

    private static void take(MinecraftServer server, ServerPlayer player, GrindstoneMenu menu, boolean shift, int expectedPotential) {
        int before = xp(server.overworld());
        int observedPotential = potential(menu, menu.getSlot(0).getItem()) + potential(menu, menu.getSlot(1).getItem());
        require(observedPotential == expectedPotential, "Unexpected per-input potential: " + observedPotential + " versus " + expectedPotential);
        var preview = menu.getSlot(2).getItem().copy();
        int levels = player.experienceLevel;
        var taken = shift ? menu.quickMoveStack(player, 2) : menu.getSlot(2).safeTake(1, 1, player);
        require(!taken.isEmpty() && ItemStack.matches(preview, taken) && !menu.getSlot(0).hasItem()
                        && !menu.getSlot(1).hasItem() && player.experienceLevel == levels,
                "Grindstone extraction changed the result, charged XP or failed consumption");
        int awarded = xp(server.overworld()) - before;
        int half = (expectedPotential + 1) / 2;
        require(awarded >= half && awarded <= (half == 0 ? 0 : 2 * half - 1),
                "Actual XP payout " + awarded + " escaped native range for potential " + expectedPotential);
        require(menu.getSlot(2).safeTake(1, 1, player).isEmpty() && xp(server.overworld()) == before + awarded,
                "Grindstone allowed a second payout/output");
        clearOrbs(server.overworld());
    }

    static int xp(ServerLevel level) {
        return level.getEntities(EntityType.EXPERIENCE_ORB, new AABB(STATION).inflate(8), Entity::isAlive).stream().mapToInt(orb -> {
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            orb.saveWithoutId(saved);
            return orb.getValue() * saved.buildResult().getIntOr("Count", 1);
        }).sum();
    }

    private static void clearOrbs(ServerLevel level) {
        level.getEntities(EntityType.EXPERIENCE_ORB, new AABB(STATION).inflate(8), Entity::isAlive).forEach(Entity::discard);
    }
}
