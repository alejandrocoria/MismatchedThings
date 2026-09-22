package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;

public final class MixedBootsGrindstonePolicy {
    private MixedBootsGrindstonePolicy() {
    }

    public static boolean rejects(ItemStack input, ItemStack additional) {
        if (!ModContent.isMixedBoots(input) && !ModContent.isMixedBoots(additional)) {
            return false;
        }
        if (!input.isEmpty() && !additional.isEmpty()) {
            return true;
        }
        ItemStack mixed = input.isEmpty() ? additional : input;
        return ModContent.getMixedBootsData(mixed).isEmpty();
    }
}
