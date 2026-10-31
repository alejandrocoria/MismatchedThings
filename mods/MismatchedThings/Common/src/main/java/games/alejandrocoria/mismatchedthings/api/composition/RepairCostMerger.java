package games.alejandrocoria.mismatchedthings.api.composition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Computes ordered repair costs without modifying sources; negative input is rejected. */
public final class RepairCostMerger {
    private static final Logger LOG = LoggerFactory.getLogger("mismatchedthings");
    private static final AtomicBoolean REPORTED_NEGATIVE_COST = new AtomicBoolean();

    private RepairCostMerger() {
    }

    public static Results merge(ItemStack first, ItemStack second, RepairCostPolicy policy) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(policy, "policy");

        int left = first.getOrDefault(DataComponents.REPAIR_COST, 0);
        int right = second.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (left < 0 || right < 0) {
            if (REPORTED_NEGATIVE_COST.compareAndSet(false, true)) {
                LOG.warn("Repair-cost projection found a negative source value; rejecting composition");
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

    public record Results(int primary, int inverse) {
    }
}
