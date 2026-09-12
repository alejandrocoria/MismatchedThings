package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;

public final class MixedBootsGrindstonePolicy {
    private MixedBootsGrindstonePolicy() {
    }

    public static boolean rejects(ItemStack input, ItemStack additional) {
        return ModContent.isMixedBoots(input) || ModContent.isMixedBoots(additional);
    }
}
