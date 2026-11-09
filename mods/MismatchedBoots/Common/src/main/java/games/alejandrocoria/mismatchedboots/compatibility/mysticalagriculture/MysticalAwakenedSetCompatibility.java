package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Boolean FEET recognition only; the original chestplate owns growth and cadence. */
public final class MysticalAwakenedSetCompatibility {
    private static final Identifier AWAKENED = Identifier.parse("mysticalagriculture:awakened_supremium_boots");
    private MysticalAwakenedSetCompatibility() {}

    public static boolean recognize(ItemStack root, boolean original) {
        if (original) { return true; }
        if (root == null || !root.is(ModContent.MIXED_BOOTS_KEY) || ItemStack.validateStrict(root).isError()) { return false; }
        var equipment = root.get(DataComponents.EQUIPPABLE);
        return equipment != null && equipment.slot() == EquipmentSlot.FEET
                && MixedBootsSources.anySource(root, source -> AWAKENED.equals(BuiltInRegistries.ITEM.getKey(source.getItem())));
    }
}

