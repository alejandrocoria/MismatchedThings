package games.alejandrocoria.mismatchedleggings.test.awakenedset;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Runtime-identical test faults plus an observer of the unmodified native growth consumer. */
public final class SetControl {
    public static int activations;
    private SetControl() {}
    @SuppressWarnings("unchecked")
    public static boolean opaque(ItemStack stack, Object expected) {
        return expected instanceof Item item ? stack.is(item) : stack.is((Holder<Item>) expected);
    }
    public static boolean peer(boolean original) { return original; }
}

