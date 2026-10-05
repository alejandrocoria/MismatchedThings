package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.component.ComponentProjection;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Three-way merge of the root orientation's old/new projections, including owner policies. */
final class ProjectedComponentMerger {
    private ProjectedComponentMerger() {
    }

    static ItemStack merge(
            ItemStack currentRoot,
            ComponentProjection.Result previous,
            ComponentProjection.Result replacement) {
        Objects.requireNonNull(currentRoot, "currentRoot");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(replacement, "replacement");

        ItemStack result = currentRoot.copy();
        Set<DataComponentType<?>> componentTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        componentTypes.addAll(previous.managedTypes());
        componentTypes.addAll(replacement.managedTypes());

        for (DataComponentType<?> type : componentTypes) {
            mergeComponent(currentRoot, previous.primary(), replacement.primary(), result, type);
        }

        return result;
    }

    private static <T> void mergeComponent(
            ItemStack currentRoot,
            DataComponentMap previous,
            DataComponentMap replacement,
            ItemStack target,
            DataComponentType<T> type) {
        if (!ComponentProjection.equivalentValues(type, currentRoot.get(type), previous.get(type))) {
            return;
        }
        T value = replacement.get(type);
        if (value == null) {
            target.remove(type);
        } else {
            target.set(type, value);
        }
    }
}
