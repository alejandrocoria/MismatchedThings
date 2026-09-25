package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class ComponentRulesGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteAndOrdinaryBootsAreBothVulnerable(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.NETHERITE_BOOTS, Items.IRON_BOOTS);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            helper.assertTrue(!root.has(DataComponents.DAMAGE_RESISTANT), "One-sided resistance survived");
            assertDamage(helper, root, false, false);
        }
        helper.assertTrue(GameTestStacks.data(pair.primary()).left().source().has(DataComponents.DAMAGE_RESISTANT),
                "Projection modified the netherite snapshot");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void sharedTagsAndExplicitIntersectionsControlRealDamage(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            helper.assertTrue(root.get(DataComponents.DAMAGE_RESISTANT).types().unwrapKey().isPresent(),
                    "Shared tag became a frozen list");
            assertDamage(helper, root, true, false);
        }
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var fire = registry.getOrThrow(DamageTypes.IN_FIRE);
        var cactus = registry.getOrThrow(DamageTypes.CACTUS);
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        left.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(HolderSet.direct(fire, cactus)));
        right.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(HolderSet.direct(cactus)));
        pair = GameTestStacks.pair(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            assertDamage(helper, root, false, true);
        }
        right.set(DataComponents.DAMAGE_RESISTANT,
                new ItemStack(Items.NETHERITE_BOOTS).get(DataComponents.DAMAGE_RESISTANT));
        pair = GameTestStacks.pair(left, right);
        assertDamage(helper, pair.primary(), false, false);
        assertDamage(helper, pair.inverse(), false, false);
        left.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(registry.getOrThrow(
                TagKey.create(Registries.DAMAGE_TYPE, Identifier.withDefaultNamespace("is_projectile")))));
        pair = GameTestStacks.pair(left, right);
        assertDamage(helper, pair.primary(), false, false);
        assertDamage(helper, pair.inverse(), false, false);
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void rootEditsSurviveWhileDerivedResistanceChanges(GameTestHelper helper) {
        ItemStack netherite = new ItemStack(Items.NETHERITE_BOOTS);
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        var pair = GameTestStacks.pair(netherite, iron);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            ItemLore lore = new ItemLore(List.of(Component.literal("Live root lore")));
            root.set(DataComponents.LORE, lore);
            ItemStack before = root.copy();
            ItemStack result = MixedBootsRecomposer.recompose(root, netherite, netherite);
            assertDamage(helper, result, true, false);
            helper.assertValueEqual(result.get(DataComponents.LORE), lore, "preserved root-only lore");
            helper.assertTrue(ItemStack.matches(root, before), "Recomposition mutated its input");
            result.remove(DataComponents.DAMAGE_RESISTANT);
            assertDamage(helper, MixedBootsRecomposer.recompose(result, netherite, netherite), false, false);
        }
        ItemStack root = GameTestStacks.pair(netherite, netherite).primary();
        assertDamage(helper, MixedBootsRecomposer.recompose(root, netherite, iron), false, false);
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void oneSidedUnbreakableDoesNotPreventDurabilityDamage(GameTestHelper helper) {
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        left.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        left.set(DataComponents.GLIDER, Unit.INSTANCE);
        var pair = GameTestStacks.pair(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            helper.assertTrue(!root.has(DataComponents.UNBREAKABLE) && !root.has(DataComponents.GLIDER),
                    "One-sided capability survived");
            helper.assertTrue(root.isDamageableItem(), "One-sided unbreakable prevents damage");
        }
        right.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        pair = GameTestStacks.pair(left, right);
        helper.assertTrue(!pair.primary().isDamageableItem() && !pair.inverse().isDamageableItem(),
                "Shared unbreakable was not retained");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void defaultDurabilityAndRepairCostUseBothSources(GameTestHelper helper) {
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        left.set(DataComponents.MAX_DAMAGE, 101);
        left.set(DataComponents.DAMAGE, 50);
        left.set(DataComponents.REPAIR_COST, 2);
        right.set(DataComponents.MAX_DAMAGE, 200);
        right.set(DataComponents.DAMAGE, 20);
        right.set(DataComponents.REPAIR_COST, 7);

        var pair = GameTestStacks.pair(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            helper.assertValueEqual(root.getMaxDamage(), 150, "average maximum durability");
            helper.assertValueEqual(root.getDamageValue(), 35, "average remaining durability");
            helper.assertValueEqual(root.getOrDefault(DataComponents.REPAIR_COST, 0), 7,
                    "maximum prior-work cost");
        }
        helper.assertValueEqual(GameTestStacks.data(pair.primary()).left().source()
                .getOrDefault(DataComponents.REPAIR_COST, 0), 2, "left snapshot prior-work cost");
        helper.assertValueEqual(GameTestStacks.data(pair.primary()).right().source()
                .getOrDefault(DataComponents.REPAIR_COST, 0), 7, "right snapshot prior-work cost");
        helper.succeed();
    }

    private static void assertDamage(GameTestHelper helper, ItemStack root, boolean fire, boolean cactus) {
        var level = helper.getLevel();
        helper.assertValueEqual(root.canBeHurtBy(level.damageSources().inFire()), !fire, "in-fire stack damage");
        helper.assertValueEqual(root.canBeHurtBy(level.damageSources().cactus()), !cactus, "cactus stack damage");
        ItemEntity fireEntity = new ItemEntity(level, 0, 0, 0, root.copy());
        helper.assertValueEqual(fireEntity.fireImmune(), fire, "entity fire immunity");
        helper.assertValueEqual(fireEntity.hurtServer(level, level.damageSources().lava(), 1), !fire, "lava entity damage");
        ItemEntity cactusEntity = new ItemEntity(level, 0, 0, 0, root.copy());
        helper.assertValueEqual(cactusEntity.hurtServer(level, level.damageSources().cactus(), 1), !cactus,
                "cactus entity damage");
    }
}
