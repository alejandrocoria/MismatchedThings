package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeMixedBootsItemTest {
    @Test
    void productionRegistrationUsesTheNeoForgeItemHook() {
        assertInstanceOf(NeoForgeMixedBootsItem.class,
                BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
    }

    @Test
    void supportsEnchantmentsAcceptedByEitherSourceOnly() {
        ItemStack supporting = feetStack(Items.IRON_SWORD);
        Holder<Enchantment> enchantment = Holder.direct(enchantmentFor(supporting));
        ItemStack unsupported = feetStack(Items.IRON_BOOTS);
        NeoForgeMixedBootsItem mixedItem = (NeoForgeMixedBootsItem)
                BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType().orElseThrow();

        ItemStack eitherSource = mixedStack(mixedItem, dataType, supporting, unsupported);
        ItemStack supportingOnRight = mixedStack(mixedItem, dataType, unsupported, supporting);
        ItemStack neitherSource = mixedStack(mixedItem, dataType, unsupported, feetStack(Items.GOLDEN_BOOTS));

        assertTrue(mixedItem.supportsEnchantment(eitherSource, enchantment));
        assertTrue(mixedItem.supportsEnchantment(supportingOnRight, enchantment));
        assertFalse(mixedItem.supportsEnchantment(neitherSource, enchantment));
    }

    private static ItemStack mixedStack(
            NeoForgeMixedBootsItem item,
            DataComponentType<MixedBootsData> dataType,
            ItemStack left,
            ItemStack right) {
        ItemStack stack = new ItemStack(Holder.direct(item, baseComponents()));
        stack.set(dataType, new MixedBootsData(new BootHalf(left), new BootHalf(right)));
        return stack;
    }

    private static ItemStack feetStack(Item item) {
        return new ItemStack(Holder.direct(item, baseComponents()));
    }

    private static DataComponentMap baseComponents() {
        return DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build();
    }

    private static Enchantment enchantmentFor(ItemStack supported) {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(supported.typeHolder()),
                1,
                1,
                Enchantment.constantCost(1),
                Enchantment.constantCost(1),
                1,
                EquipmentSlotGroup.FEET);
        return new Enchantment(Component.literal("NeoForge hook test"), definition,
                HolderSet.empty(), DataComponentMap.EMPTY);
    }
}
