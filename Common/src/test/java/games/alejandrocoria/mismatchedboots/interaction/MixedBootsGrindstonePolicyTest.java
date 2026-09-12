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
    void rejectsMixedBootsInEitherInputWithoutAffectingOrdinaryItems() {
        ItemStack mixed = boots(Items.CHAINMAIL_BOOTS);
        mixed.set(mixedDataType, new MixedBootsData(
                new BootHalf(boots(Items.IRON_BOOTS)),
                new BootHalf(boots(Items.GOLDEN_BOOTS))));
        ItemStack ordinary = boots(Items.IRON_BOOTS);

        assertTrue(MixedBootsGrindstonePolicy.rejects(mixed, ItemStack.EMPTY));
        assertTrue(MixedBootsGrindstonePolicy.rejects(ItemStack.EMPTY, mixed));
        assertTrue(MixedBootsGrindstonePolicy.rejects(mixed, ordinary));
        assertTrue(MixedBootsGrindstonePolicy.rejects(mixed, mixed));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ordinary, ItemStack.EMPTY));
        assertFalse(MixedBootsGrindstonePolicy.rejects(ordinary, ordinary));
    }

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build()));
    }
}
