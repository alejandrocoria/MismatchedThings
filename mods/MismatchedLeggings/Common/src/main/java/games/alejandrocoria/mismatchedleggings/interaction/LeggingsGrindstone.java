package games.alejandrocoria.mismatchedleggings.interaction;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class LeggingsGrindstone {
    private LeggingsGrindstone() {
    }

    public static boolean rejects(ItemStack first, ItemStack second) {
        if (!MixedLeggingsStructure.isMixedItem(first) && !MixedLeggingsStructure.isMixedItem(second)) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return MixedLeggingsStructure.validData(first.isEmpty() ? second : first).isEmpty();
        }
        return !canRepair(first, second) && !canRepair(second, first);
    }

    public static boolean canRepair(ItemStack mixed, ItemStack donor) {
        return mixed.getItem() instanceof MixedLeggingsItem item && item.canGrindstoneRepairWith(mixed, donor);
    }

    public static ItemStack restoreDurability(ItemStack result, ItemStack mixed, ItemStack donor) {
        if (!result.isEmpty()) {
            // Native merge uses the larger input capacity; the mixed root keeps its paid capacity.
            result.set(DataComponents.MAX_DAMAGE, mixed.getMaxDamage());
            result.setDamageValue(LeggingsRepair.craftingDamage(mixed, donor));
        }
        return result;
    }
}
