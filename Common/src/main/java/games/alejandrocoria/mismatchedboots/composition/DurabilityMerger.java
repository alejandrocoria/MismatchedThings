package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

final class DurabilityMerger {
    private DurabilityMerger() {
    }

    static Result merge(ItemStack left, ItemStack right) {
        long maximumSum = (long) maximum(left) + maximum(right);
        long remainingSum = remaining(left) + remaining(right);
        int maximum = (int) (maximumSum / 2L);
        int remaining = (int) Math.min(remainingSum / 2L, maximum);
        return new Result(maximum, maximum - remaining);
    }

    private static int maximum(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) ? Math.max(stack.getMaxDamage(), 0) : 0;
    }

    private static long remaining(ItemStack stack) {
        int maximum = maximum(stack);
        long damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        return Math.max(0L, Math.min((long) maximum - damage, maximum));
    }

    record Result(int maximum, int damage) {
        boolean damageable() {
            return maximum > 0;
        }
    }
}
