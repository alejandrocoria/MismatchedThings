package games.alejandrocoria.mismatchedboots.compatibility.rei;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MismatchedWorkbenchReiExamplesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void candidatesAreValidCountOneExactUniqueAndStablySorted() {
        ItemStack secondIronVariant = named(boots(Items.IRON_BOOTS), "second iron");
        ItemStack firstIronVariant = named(boots(Items.IRON_BOOTS), "first iron");
        ItemStack duplicateFirstIron = firstIronVariant.copyWithCount(8);

        List<ItemStack> candidates = MismatchedWorkbenchReiExamples.collectCandidates(List.of(
                secondIronVariant,
                boots(Items.DIAMOND_BOOTS),
                equippable(Items.IRON_HELMET, EquipmentSlot.HEAD),
                firstIronVariant,
                duplicateFirstIron));

        assertEquals(List.of(Items.DIAMOND_BOOTS, Items.IRON_BOOTS, Items.IRON_BOOTS),
                candidates.stream().map(ItemStack::getItem).toList());
        assertEquals(List.of("second iron", "first iron"),
                candidates.subList(1, 3).stream().map(stack -> stack.getHoverName().getString()).toList());
        assertTrue(candidates.stream().allMatch(stack -> stack.getCount() == 1));
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack boots(Item item) {
        return equippable(item, EquipmentSlot.FEET);
    }

    private static ItemStack equippable(Item item, EquipmentSlot slot) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build())
                .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }
}
