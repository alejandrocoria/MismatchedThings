package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Boolean LEGS recognition only; the original chestplate owns growth and cadence. */
public final class MysticalAwakenedSetCompatibility {
    private static final Identifier AWAKENED = Identifier.parse("mysticalagriculture:awakened_supremium_leggings");
    private MysticalAwakenedSetCompatibility() {}

    public static boolean recognize(ItemStack root, boolean original) {
        if (original) { return true; }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        return data != null && (AWAKENED.equals(BuiltInRegistries.ITEM.getKey(data.left().source().getItem()))
                || AWAKENED.equals(BuiltInRegistries.ITEM.getKey(data.right().source().getItem())));
    }
}

