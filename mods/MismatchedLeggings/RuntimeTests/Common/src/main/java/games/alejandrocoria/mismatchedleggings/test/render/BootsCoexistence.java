package games.alejandrocoria.mismatchedleggings.test.render;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Optional packaged Boots, without a compile/runtime dependency in standalone profiles. */
public final class BootsCoexistence {
    public static boolean ironSeen;
    public static boolean goldSeen;

    private BootsCoexistence() {}

    public static ItemStack mixed() {
        return pair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).getFirst();
    }

    public static List<ItemStack> pair(ItemStack first, ItemStack second) {
        try {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                    .invoke(null, first, second);
            return List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair),
                    (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot construct packaged Boots control", failure);
        }
    }
}
