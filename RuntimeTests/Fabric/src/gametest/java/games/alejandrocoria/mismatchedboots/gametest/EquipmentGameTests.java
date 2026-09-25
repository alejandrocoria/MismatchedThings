package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class EquipmentGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void currentIronGoldCompositionSuppliesOneAndAHalfArmor(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, BlockPos.ZERO);
        var ironArmor = new ItemStack(Items.IRON_BOOTS)
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers()
                .stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Iron boots have no armor modifier"));
        var goldArmor = new ItemStack(Items.GOLDEN_BOOTS)
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers()
                .stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Golden boots have no armor modifier"));
        helper.assertValueEqual(ironArmor.modifier().id(), goldArmor.modifier().id(),
                "vanilla boot armor modifier identity");
        helper.assertValueEqual(ironArmor.modifier().id(), Identifier.withDefaultNamespace("armor.boots"),
                "vanilla boot armor modifier ID");

        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        stand.setItemSlot(EquipmentSlot.FEET, mixed);

        var armorEntries = mixed.getOrDefault(
                        DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY)
                .modifiers()
                .stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR))
                .toList();
        helper.assertValueEqual(armorEntries.size(), 1, "iron/gold stored armor entries");
        helper.assertValueEqual(armorEntries.getFirst().modifier().id(), ironArmor.modifier().id(),
                "iron/gold stored armor modifier ID");
        helper.assertTrue(Math.abs(armorEntries.getFirst().modifier().amount() - 1.5D) < 1.0E-12D,
                "Iron/gold stored armor amount was not 1.5: " + armorEntries);
        helper.succeedWhen(() -> helper.assertTrue(
                Math.abs(stand.getAttributeValue(Attributes.ARMOR) - 1.5D) < 1.0E-12D,
                "Iron/gold equipped armor was not 1.5: "
                        + stand.getAttributeValue(Attributes.ARMOR)));
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void durabilityChangesOnlyTheRootAndBreakingRemovesIt(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, BlockPos.ZERO);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData snapshots = GameTestStacks.data(mixed);
        stand.setItemSlot(EquipmentSlot.FEET, mixed);

        mixed.hurtAndBreak(1, stand, EquipmentSlot.FEET);
        helper.assertValueEqual(mixed.getDamageValue(), 1, "mixed boots root damage");
        helper.assertTrue(GameTestStacks.data(mixed).equals(snapshots), "Root damage changed nested sources");

        mixed.setDamageValue(mixed.getMaxDamage() - 1);
        mixed.hurtAndBreak(1, stand, EquipmentSlot.FEET);
        helper.assertTrue(stand.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                "Broken mixed boots remained equipped");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void unbreakableAndUnbreakingFollowVanillaDurabilityPaths(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, BlockPos.ZERO);
        ItemStack owner = new ItemStack(Items.IRON_BOOTS);
        owner.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        ItemStack other = new ItemStack(Items.GOLDEN_BOOTS);
        ItemStack oneSided = GameTestStacks.pair(owner, other).primary();
        oneSided.hurtAndBreak(1, stand, EquipmentSlot.FEET);
        helper.assertValueEqual(oneSided.getDamageValue(), 1, "one-sided unbreakable must not prevent damage");
        other.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        ItemStack unbreakable = GameTestStacks.pair(owner, other).primary();
        unbreakable.hurtAndBreak(100, stand, EquipmentSlot.FEET);
        helper.assertValueEqual(unbreakable.getDamageValue(), 0, "unbreakable mixed boots damage");

        ItemStack enchantedSource = new ItemStack(Items.IRON_BOOTS);
        enchantedSource.set(DataComponents.MAX_DAMAGE, 10000);
        setEnchantment(enchantedSource, enchantment(helper, Enchantments.UNBREAKING), 3);
        ItemStack otherSource = new ItemStack(Items.GOLDEN_BOOTS);
        otherSource.set(DataComponents.MAX_DAMAGE, 10000);
        ItemStack withUnbreaking = GameTestStacks.pair(enchantedSource, otherSource).primary();
        withUnbreaking.hurtAndBreak(1000, stand, EquipmentSlot.FEET);
        helper.assertTrue(withUnbreaking.getDamageValue() > 0 && withUnbreaking.getDamageValue() < 1000,
                "Unbreaking did not reduce durability loss through the vanilla path: " + withUnbreaking.getDamageValue());
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void mendingRepairsRootDamageWithoutChangingSources(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack source = new ItemStack(Items.IRON_BOOTS);
        setEnchantment(source, enchantment(helper, Enchantments.MENDING), 1);
        ItemStack mixed = GameTestStacks.pair(source, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        mixed.setDamageValue(100);
        MixedBootsData snapshots = GameTestStacks.data(mixed);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), player.getX(), player.getY(), player.getZ(), 100);

        orb.playerTouch(player);

        helper.assertTrue(mixed.getDamageValue() < 100, "Mending XP did not repair root damage");
        helper.assertTrue(GameTestStacks.data(mixed).equals(snapshots), "Mending changed nested sources");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void inventoryShiftClickEquipsMixedBoots(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        player.getInventory().setItem(0, mixed);
        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);

        ItemStack moved = menu.quickMoveStack(player, 36);

        GameTestStacks.assertOrientation(helper, moved,
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "shift-click return value");
        GameTestStacks.assertOrientation(helper, player.getItemBySlot(EquipmentSlot.FEET),
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "equipped stack");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Inventory shift-click retained a duplicate mixed stack in its source slot");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void inventoryClickSwapsMixedBootsWithEquippedBoots(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        player.getInventory().setItem(0, mixed);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);

        menu.clicked(36, 0, ContainerInput.PICKUP, player);
        menu.clicked(8, 0, ContainerInput.PICKUP, player);

        GameTestStacks.assertOrientation(helper, player.getItemBySlot(EquipmentSlot.FEET),
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "swapped equipped stack");
        helper.assertTrue(menu.getCarried().is(Items.LEATHER_BOOTS),
                "Equipment swap did not return the previously equipped boots to the cursor");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Equipment swap retained a duplicate mixed stack in the inventory");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void dispenserEquipsMixedBoots(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
        DispenserBlockEntity dispenser = helper.getBlockEntity(BlockPos.ZERO, DispenserBlockEntity.class);
        dispenser.setItem(0, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(0, 0, 1));

        helper.tickBlock(BlockPos.ZERO);

        GameTestStacks.assertOrientation(helper, stand.getItemBySlot(EquipmentSlot.FEET),
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "dispenser-equipped stack");
        helper.assertTrue(dispenser.isEmpty(), "Dispenser retained an item after equipping the mixed boots");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void deathDropKeepsOneValidMixedStackAndItsSources(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData snapshots = GameTestStacks.data(mixed);
        zombie.setItemSlot(EquipmentSlot.FEET, mixed);
        zombie.setGuaranteedDrop(EquipmentSlot.FEET);

        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);

        AABB bounds = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(3.0);
        List<ItemStack> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, Entity::isAlive).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ModContent.MIXED_BOOTS_KEY))
                .toList();
        helper.assertValueEqual(drops.size(), 1, "mixed boots death drops");
        ItemStack dropped = drops.getFirst();
        GameTestStacks.assertOrientation(helper, dropped,
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "death drop");
        helper.assertTrue(GameTestStacks.data(dropped).equals(snapshots),
                "Death drop changed nested sources");
        helper.succeed();
    }

    private static Holder<Enchantment> enchantment(
            GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static void setEnchantment(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }
}
