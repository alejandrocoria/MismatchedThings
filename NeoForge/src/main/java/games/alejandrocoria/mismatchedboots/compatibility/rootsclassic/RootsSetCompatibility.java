package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Only the audited FEET predicate is extended; other optional armor adapters own their slots. */
public final class RootsSetCompatibility {
    private RootsSetCompatibility() {
    }

    public static boolean sylvan(ItemStack feet, boolean original) {
        return original || recognizes(feet, "sylvan_boots");
    }

    public static boolean wildwood(ItemStack feet, Item actual, Object expected) {
        return actual == expected || recognizes(feet, "wildwood_boots");
    }

    private static boolean recognizes(ItemStack feet, String path) {
        var equipped = feet.get(DataComponents.EQUIPPABLE);
        return equipped != null && equipped.slot() == EquipmentSlot.FEET
                && MixedBootsSources.anySource(feet, source -> source.is(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("rootsclassic", path))));
    }
}
