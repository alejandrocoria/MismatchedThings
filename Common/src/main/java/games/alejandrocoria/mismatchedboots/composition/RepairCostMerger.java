package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class RepairCostMerger {
    private static final AtomicBoolean REPORTED_NEGATIVE_COST = new AtomicBoolean();

    private RepairCostMerger() {
    }

    static Results merge(ItemStack first, ItemStack second, RepairCostPolicy policy) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(policy, "policy");

        int left = first.getOrDefault(DataComponents.REPAIR_COST, 0);
        int right = second.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (left < 0 || right < 0) {
            if (REPORTED_NEGATIVE_COST.compareAndSet(false, true)) {
                Constants.LOG.warn("Repair-cost projection found a negative source value; rejecting composition");
            }
            throw new IllegalArgumentException("Invalid negative repair-cost input");
        }

        return switch (policy) {
            case OWNER -> new Results(left, right);
            case AVERAGE -> shared((int) (((long) left + right + 1L) / 2L));
            case MINIMUM -> shared(Math.min(left, right));
            case MAXIMUM -> shared(Math.max(left, right));
        };
    }

    private static Results shared(int value) {
        return new Results(value, value);
    }

    record Results(int primary, int inverse) {
    }
}
