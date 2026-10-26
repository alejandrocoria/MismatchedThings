package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

final class DurabilityMerger {
    private DurabilityMerger() {
    }

    static Results merge(ItemStack left, ItemStack right, DurabilityPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return switch (policy) {
            case AVERAGE -> {
                Result result = average(left, right);
                yield new Results(result, result);
            }
            case SEPARATE -> new Results(separate(left), separate(right));
        };
    }

    private static Result average(ItemStack left, ItemStack right) {
        long maximumSum = (long) maximum(left) + maximum(right);
        long remainingSum = remaining(left) + remaining(right);
        int maximum = (int) (maximumSum / 2L);
        int remaining = (int) Math.min(remainingSum / 2L, maximum);
        return new Result(maximum, maximum - remaining);
    }

    private static Result separate(ItemStack stack) {
        int maximum = maximum(stack);
        return new Result(maximum, maximum - (int) remaining(stack));
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

    record Results(Result primary, Result inverse) {
    }
}
