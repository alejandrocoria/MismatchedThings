package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class RepairableComposer {
    private RepairableComposer() {
    }

    static Optional<Repairable> compose(ItemStack first, ItemStack second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        List<Holder<Item>> acceptedItems = new ArrayList<>();
        Set<Item> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
        addAcceptedItems(first.get(DataComponents.REPAIRABLE), acceptedItems, seenItems);
        addAcceptedItems(second.get(DataComponents.REPAIRABLE), acceptedItems, seenItems);

        if (acceptedItems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Repairable(HolderSet.direct(acceptedItems)));
    }

    private static void addAcceptedItems(
            Repairable repairable,
            List<Holder<Item>> acceptedItems,
            Set<Item> seenItems) {
        if (repairable == null) {
            return;
        }

        repairable.items().stream()
                  .filter(holder -> seenItems.add(holder.value()))
                  .forEach(acceptedItems::add);
    }
}
