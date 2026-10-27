package games.alejandrocoria.mismatchedthings.internal;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;

public final class WorkbenchContributors {
    // Consumer constructors may run concurrently on NeoForge. Sorted IDs also give deterministic query order.
    private static final Map<Identifier, Predicate<ItemStack>> INPUTS = new ConcurrentSkipListMap<>();

    private WorkbenchContributors() {
    }

    public static void register(Identifier owner, Predicate<ItemStack> accepts) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(accepts, "accepts");
        if (INPUTS.putIfAbsent(owner, accepts) != null) {
            throw new IllegalStateException("Duplicate workbench contributor: " + owner);
        }
    }

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Predicate<ItemStack> predicate : INPUTS.values()) {
            if (predicate.test(stack.copyWithCount(1))) {
                return true;
            }
        }
        return false;
    }
}
