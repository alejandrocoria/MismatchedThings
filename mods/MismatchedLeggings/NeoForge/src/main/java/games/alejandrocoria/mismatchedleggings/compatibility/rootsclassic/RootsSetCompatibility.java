package games.alejandrocoria.mismatchedleggings.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Only mixed LEGS recognition is extended; native results and other slots keep their owners. */
public final class RootsSetCompatibility {
    private static final Identifier SYLVAN = Identifier.parse("rootsclassic:sylvan_tunic");
    private static final Identifier WILDWOOD = Identifier.parse("rootsclassic:wildwood_leggings");

    private RootsSetCompatibility() {
    }

    public static boolean sylvan(ItemStack legs, boolean original) {
        return original || recognizes(legs, SYLVAN);
    }

    public static boolean wildwood(ItemStack legs, Item actual, Object expected) {
        return actual == expected || recognizes(legs, WILDWOOD);
    }

    private static boolean recognizes(ItemStack root, Identifier item) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        return data != null && (item.equals(BuiltInRegistries.ITEM.getKey(data.left().source().getItem()))
                || item.equals(BuiltInRegistries.ITEM.getKey(data.right().source().getItem())));
    }
}
