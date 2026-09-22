package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

final class OwnerComponentMerger {
    private OwnerComponentMerger() {
    }

    static ItemStack merge(
            ItemStack currentRoot,
            ItemStack previousOwner,
            ItemStack newOwner,
            Set<DataComponentType<?>> managedTypes) {
        Objects.requireNonNull(currentRoot, "currentRoot");
        Objects.requireNonNull(previousOwner, "previousOwner");
        Objects.requireNonNull(newOwner, "newOwner");
        Objects.requireNonNull(managedTypes, "managedTypes");

        ItemStack result = currentRoot.copy();
        Set<DataComponentType<?>> componentTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        componentTypes.addAll(currentRoot.getComponents().keySet());
        componentTypes.addAll(previousOwner.getComponents().keySet());
        componentTypes.addAll(newOwner.getComponents().keySet());

        for (DataComponentType<?> type : componentTypes) {
            if (!managedTypes.contains(type)
                    && Objects.equals(currentRoot.get(type), previousOwner.get(type))) {
                copyComponent(newOwner, result, type);
            }
        }

        return result;
    }

    private static <T> void copyComponent(
            ItemStack source,
            ItemStack target,
            DataComponentType<T> type) {
        T value = source.get(type);
        if (value == null) {
            target.remove(type);
        } else {
            target.set(type, value);
        }
    }
}
