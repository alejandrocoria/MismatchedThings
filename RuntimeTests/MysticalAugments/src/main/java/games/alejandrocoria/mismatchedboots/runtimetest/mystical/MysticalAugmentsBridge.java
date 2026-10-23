package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/** Native test API helpers plus the retained, separately gated pre-B refund experiment. */
public final class MysticalAugmentsBridge {
    public static boolean refundsActive;

    private MysticalAugmentsBridge() {}

    public static boolean enabled() {
        return !System.getProperty("mismatchedboots.test.mysticalAugments", "").isEmpty();
    }

    /** Refund ownership is per original input, not the deduplicated/strongest effective list. */
    public static ItemStack refundSource(ItemStack root) {
        if (!refundsActive || !ModContent.isMixedBoots(root) || root.getCount() != 1) { return null; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return null; }
        ItemStack owner = data.left().source();
        return owner.getItem().getClass().getName().equals(
                "com.blakebr0.mysticalagriculture.item.armor.EssenceBootsItem") ? owner : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> nativeAugments(ItemStack stack) {
        return (List<Object>) call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "getAugments",
                new Class<?>[]{ItemStack.class}, stack);
    }

    public static Object call(String type, String method, Class<?>[] types, Object... args) {
        try { return Class.forName(type).getMethod(method, types).invoke(null, args); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(type + "." + method, exception); }
    }

    public static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(name, exception); }
    }
}
