package games.alejandrocoria.mismatchedthings.internal;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;

public final class WorkbenchContributors {
    // Consumer constructors may run concurrently on NeoForge. Sorted IDs also give deterministic query order.
    private static final Map<Identifier, Contributor> INPUTS = new ConcurrentSkipListMap<>();

    private WorkbenchContributors() {
    }

    public static void register(Identifier owner, Predicate<ItemStack> accepts) {
        register(owner, accepts, null);
    }

    public static void register(Identifier owner, Predicate<ItemStack> accepts, @Nullable Identifier emptySlotIcon) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(accepts, "accepts");
        if (INPUTS.putIfAbsent(owner, new Contributor(accepts, emptySlotIcon)) != null) {
            throw new IllegalStateException("Duplicate workbench contributor: " + owner);
        }
    }

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Contributor contributor : INPUTS.values()) {
            if (contributor.accepts().test(stack.copyWithCount(1))) {
                return true;
            }
        }
        return false;
    }

    /** All contributed hints when empty, or only hints admitting the opposite input. */
    public static List<Identifier> emptySlotIcons(ItemStack oppositeInput) {
        var icons = new LinkedHashSet<Identifier>();
        for (Contributor contributor : INPUTS.values()) {
            if (contributor.icon() != null && (oppositeInput.isEmpty()
                    || contributor.accepts().test(oppositeInput.copyWithCount(1)))) {
                icons.add(contributor.icon());
            }
        }
        return List.copyOf(icons);
    }

    private record Contributor(Predicate<ItemStack> accepts, @Nullable Identifier icon) {
    }
}
