package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/** Native test API helpers; no candidate effect or refund owner remains. */
public final class MysticalNativeTestApi {

    private MysticalNativeTestApi() {
    }

    public static boolean enabled() {
        return !System.getProperty("mismatchedboots.test.mysticalAugments", "").isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> nativeAugments(ItemStack stack) {
        return (List<Object>) call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "getAugments",
                                   new Class<?>[]{ItemStack.class}, stack);
    }

    public static Object call(String type, String method, Class<?>[] types, Object... args) {
        try {
            return Class.forName(type).getMethod(method, types).invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(type + "." + method, exception);
        }
    }

    public static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(name, exception);
        }
    }
}
