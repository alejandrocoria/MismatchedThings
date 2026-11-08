package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;


import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native fixture construction only; all mixed selection/dispatch comes from production. */
public final class NativeAugments {
    private static final String PREFIX = "com.blakebr0.mysticalagriculture.";
    private NativeAugments() {}

    public static Object invoke(Object receiver, String name, Class<?>[] types, Object... args) {
        try {
            return (receiver instanceof Class<?> type ? type : receiver.getClass()).getMethod(name, types)
                    .invoke(receiver instanceof Class<?> ? null : receiver, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    public static Class<?> type(String name) {
        try { return Class.forName(PREFIX + name); } catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
    }

    public static Object augment(String name) {
        try { return type("lib.ModAugments").getField(name).get(null); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static ItemStack item(String path, String... entries) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mysticalagriculture:" + path)));
        require(!stack.isEmpty(), "Missing native " + path);
        for (int slot = 0; slot < entries.length; slot++) {
            Object augment = augment(entries[slot]);
            require(Boolean.TRUE.equals(invoke(stack.getItem(), "canApplyAugment", new Class<?>[]{type("api.tinkering.Augment")}, augment)), "Bad native source installation");
            invoke(type("api.util.AugmentUtils"), "addAugment", new Class<?>[]{ItemStack.class, type("api.tinkering.Augment"), int.class}, stack, augment, slot);
        }
        return stack;
    }

    public static String id(Object augment) {
        return invoke(augment, "getId", new Class<?>[0]).toString();
    }

    public static ItemStack mixed(String field, boolean inverse) {
        var first = item("awakened_supremium_leggings", field);
        var second = item("inferium_leggings", "LUCK_I");
        var pair = MixedLeggingsFactory.createPair(first, second);
        return inverse ? pair.inverse() : pair.primary();
    }
}
