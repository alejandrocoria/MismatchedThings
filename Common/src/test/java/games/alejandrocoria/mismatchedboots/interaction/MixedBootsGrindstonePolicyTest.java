package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsGrindstonePolicyTest {
    private static DataComponentType<MixedBootsData> mixedDataType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
    }

    @Test
    void acceptsEitherSourceDonorAndRejectsOtherPairs() {
        ItemStack mixed = mixed();
        ItemStack ordinary = boots(Items.IRON_BOOTS);
        ItemStack otherSource = boots(Items.GOLDEN_BOOTS);

        assertFalse(MixedBootsGrindstonePolicy.rejects(mixed, ItemStack.EMPTY));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ItemStack.EMPTY, mixed));
        assertFalse(MixedBootsGrindstonePolicy.rejects(mixed, ordinary));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ordinary, mixed));
        assertFalse(MixedBootsGrindstonePolicy.rejects(mixed, otherSource));
        assertTrue(MixedBootsGrindstonePolicy.canUseOrdinaryDonor(mixed, ordinary));
        assertTrue(MixedBootsGrindstonePolicy.canUseOrdinaryDonor(mixed, otherSource));
        assertFalse(MixedBootsGrindstonePolicy.canUseOrdinaryDonor(ordinary, mixed));
        assertTrue(MixedBootsGrindstonePolicy.rejects(mixed, boots(Items.DIAMOND_BOOTS)));
        assertTrue(MixedBootsGrindstonePolicy.rejects(mixed, mixed));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ordinary, ItemStack.EMPTY));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ordinary, ordinary));
    }

    @Test
    void restoresRootMaximumAndCalculatesBoundedRepair() {
        ItemStack mixed = mixed();
        mixed.setDamageValue(150);
        ItemStack donor = boots(Items.IRON_BOOTS);
        donor.set(DataComponents.MAX_DAMAGE, 500);
        donor.setDamageValue(490);
        ItemStack output = mixed.copy();
        output.set(DataComponents.MAX_DAMAGE, 500);

        MixedBootsGrindstonePolicy.restoreDurability(output, mixed, donor);

        assertEquals(200, output.getMaxDamage());
        assertEquals(130, output.getDamageValue());
        assertEquals(150, mixed.getDamageValue());
        assertEquals(490, donor.getDamageValue());
        assertEquals(ModContent.getMixedBootsData(mixed), ModContent.getMixedBootsData(output));

        donor.setDamageValue(0);
        MixedBootsGrindstonePolicy.restoreDurability(output, mixed, donor);
        assertEquals(0, output.getDamageValue());

        mixed.setDamageValue(200);
        donor.setDamageValue(500);
        MixedBootsGrindstonePolicy.restoreDurability(output, mixed, donor);
        assertEquals(190, output.getDamageValue());
    }

    private static ItemStack mixed() {
        ItemStack mixed = boots(Items.CHAINMAIL_BOOTS);
        mixed.set(DataComponents.MAX_DAMAGE, 200);
        mixed.set(DataComponents.DAMAGE, 0);
        mixed.set(mixedDataType, new MixedBootsData(
                new BootHalf(boots(Items.IRON_BOOTS)),
                new BootHalf(boots(Items.GOLDEN_BOOTS))));
        return mixed;
    }

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build()));
    }
}
