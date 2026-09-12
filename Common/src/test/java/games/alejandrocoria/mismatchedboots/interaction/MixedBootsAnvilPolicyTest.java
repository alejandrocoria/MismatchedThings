package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsAnvilPolicyTest {
    private static DataComponentType<MixedBootsData> mixedDataType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
    }

    @Test
    void recognizesMixedDataAndRejectsOnlyTwoMixedInputs() {
        ItemStack firstMixed = mixedBoots(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack secondMixed = mixedBoots(Items.DIAMOND_BOOTS, Items.LEATHER_BOOTS);
        ItemStack ordinary = boots(Items.CHAINMAIL_BOOTS);

        assertTrue(ModContent.isMixedBoots(firstMixed));
        assertTrue(MixedBootsAnvilPolicy.rejectsCombination(firstMixed, secondMixed));
        assertFalse(MixedBootsAnvilPolicy.rejectsCombination(firstMixed, ordinary));
        assertFalse(MixedBootsAnvilPolicy.rejectsCombination(ordinary, secondMixed));
        assertFalse(MixedBootsAnvilPolicy.rejectsCombination(ordinary, ordinary));
    }

    @Test
    void allowsAnvilEnchantmentsSupportedByEitherSource() {
        ItemStack mixed = mixedBoots(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        Enchantment ironEnchantment = enchantmentFor(boots(Items.IRON_BOOTS));
        Enchantment goldEnchantment = enchantmentFor(boots(Items.GOLDEN_BOOTS));

        assertFalse(ironEnchantment.canEnchant(mixed));
        assertFalse(goldEnchantment.canEnchant(mixed));
        assertTrue(MixedBootsAnvilPolicy.canApplyEnchantment(ironEnchantment, mixed, false));
        assertTrue(MixedBootsAnvilPolicy.canApplyEnchantment(goldEnchantment, mixed, false));
    }

    @Test
    void doesNotAllowEnchantmentsUnsupportedByBothSources() {
        ItemStack mixed = mixedBoots(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        Enchantment unrelated = enchantmentFor(boots(Items.DIAMOND_BOOTS));

        assertFalse(MixedBootsAnvilPolicy.canApplyEnchantment(unrelated, mixed, false));
    }

    @Test
    void preservesVanillaEnchantabilityForOrdinaryItems() {
        ItemStack supported = boots(Items.IRON_BOOTS);
        ItemStack unsupported = boots(Items.GOLDEN_BOOTS);
        Enchantment enchantment = enchantmentFor(supported);

        assertTrue(MixedBootsAnvilPolicy.canApplyEnchantment(enchantment, supported, true));
        assertFalse(MixedBootsAnvilPolicy.canApplyEnchantment(enchantment, unsupported, false));
    }

    private static ItemStack mixedBoots(Item leftItem, Item rightItem) {
        ItemStack left = boots(leftItem);
        ItemStack right = boots(rightItem);
        ItemStack mixed = boots(Items.CHAINMAIL_BOOTS);
        mixed.set(mixedDataType, new MixedBootsData(new BootHalf(left), new BootHalf(right)));
        return mixed;
    }

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build()));
    }

    private static Enchantment enchantmentFor(ItemStack supported) {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(supported.typeHolder()),
                1,
                3,
                Enchantment.constantCost(1),
                Enchantment.constantCost(1),
                1,
                EquipmentSlotGroup.FEET);
        return new Enchantment(Component.literal("test"), definition, HolderSet.empty(), DataComponentMap.EMPTY);
    }
}
