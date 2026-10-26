package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

public final class MixedBootsSources {
    private MixedBootsSources() {
    }

    /**
     * Strict gameplay boundary: unlike ModContent's broad detection, require our
     * item, registered data and two valid non-mixed sources before invoking consumers.
     */
    public static boolean anySource(ItemStack stack, Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        if (stack == null || stack.isEmpty() || stack.getCount() != 1
                || !(stack.getItem() instanceof MixedBootsItem)) {
            return false;
        }

        MixedBootsData data = ModContent.suspendedBootsData(stack).isPresent() ? null
                                                                               : ModContent.mixedBootsDataType().map(stack::get).orElse(null);
        return anySource(data, predicate);
    }

    static boolean anySource(MixedBootsData data, Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        if (data == null) {
            return false;
        }

        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) {
            return false;
        }

        return predicate.test(left) || predicate.test(right);
    }
}
