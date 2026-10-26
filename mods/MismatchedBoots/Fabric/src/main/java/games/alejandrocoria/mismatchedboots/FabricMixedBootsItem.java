package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class FabricMixedBootsItem extends MixedBootsItem {
    public FabricMixedBootsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        MysticalAugmentsCompatibility.tick(stack, level, entity, slot);
    }
}
