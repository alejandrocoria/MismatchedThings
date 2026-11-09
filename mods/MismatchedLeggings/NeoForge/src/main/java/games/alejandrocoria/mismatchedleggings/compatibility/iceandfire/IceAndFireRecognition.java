package games.alejandrocoria.mismatchedleggings.compatibility.iceandfire;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Boolean source recognition only; the native event owns selection, coefficients and set logic. */
public final class IceAndFireRecognition {
    private IceAndFireRecognition() {}

    public static boolean armor(ItemStack legs, boolean original, Class<?> nativeType) {
        if (original) { return true; }
        return MixedLeggingsStructure.validData(legs).filter(data ->
                nativeType.isInstance(data.left().source().getItem())
                        || nativeType.isInstance(data.right().source().getItem())).isPresent();
    }

    public static boolean lightning(ItemStack legs, Object exactItem, boolean original) {
        if (original) { return true; }
        return exactItem instanceof Item item && MixedLeggingsStructure.validData(legs).filter(data ->
                data.left().source().is(item) || data.right().source().is(item)).isPresent();
    }
}
