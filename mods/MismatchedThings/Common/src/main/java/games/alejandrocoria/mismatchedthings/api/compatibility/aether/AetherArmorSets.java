package games.alejandrocoria.mismatchedthings.api.compatibility.aether;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Optional native value access; equipment admission and counting remain product-local. */
public final class AetherArmorSets {
    private AetherArmorSets() {}

    @SuppressWarnings("unchecked")
    public static TagKey<Item> tag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof TagKey<?> tag) {
            return (TagKey<Item>) tag;
        }
        try {
            if (value.getClass() != Native.VALUE) {
                throw new IllegalStateException("Unknown Aether armor set value: " + value.getClass().getName());
            }
            return (TagKey<Item>) Native.ARMOR.invoke(value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read Aether armor set", failure);
        }
    }

    public static Object value(TagKey<Item> tag) {
        try {
            return Native.CONSTRUCTOR.newInstance(tag);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot construct Aether armor set", failure);
        }
    }

    // Loaded only for a record query, never during optional-mod bootstrap or class transformation.
    private static final class Native {
        private static final Class<?> VALUE;
        private static final Method ARMOR;
        private static final Constructor<?> CONSTRUCTOR;

        static {
            try {
                VALUE = Class.forName("com.aetherteam.aetherii.item.components.ArmorSet");
                ARMOR = VALUE.getMethod("armor");
                CONSTRUCTOR = VALUE.getConstructor(TagKey.class);
                if (!VALUE.isRecord() || ARMOR.getReturnType() != TagKey.class) {
                    throw new IllegalStateException("Unknown Aether armor set contract");
                }
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
