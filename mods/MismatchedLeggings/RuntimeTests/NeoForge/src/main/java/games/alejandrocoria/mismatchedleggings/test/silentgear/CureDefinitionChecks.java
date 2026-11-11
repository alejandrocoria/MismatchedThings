package games.alejandrocoria.mismatchedleggings.test.silentgear;

import net.minecraft.world.effect.MobEffects;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition;
import net.silentchaos512.gear.setup.gear.GearTypes;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.trait;

/** Current data admission through the actual private binding; no public production API added for tests. */
final class CureDefinitionChecks {
    private CureDefinitionChecks() {}

    static void run() throws Exception {
        var apiField = Class.forName("games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCures").getDeclaredField("api");
        apiField.setAccessible(true);
        Object api = apiField.get(null);
        require(api != null, "Cure binding is absent despite readiness");
        var admitted = api.getClass().getDeclaredMethod("admitted", Object.class, String.class);
        admitted.setAccessible(true);
        for (String target : List.of("poison", "wither")) {
            var instance = trait("cure_" + target, 1);
            var definition = instance.getTrait();
            var effect = definition.getEffects().getFirst();
            require(admitted.invoke(api, instance, target) == effect, "Pinned concrete cancellation was not admitted");
            var effects = field(definition, "effects");
            var original = effects.get(definition);
            try {
                for (var invalid : List.of(List.of(), List.of(effect, effect), trait("renew", 1).getTrait().getEffects())) {
                    effects.set(definition, invalid);
                    refused(admitted, api, instance, target);
                }
            } finally {
                effects.set(definition, original);
            }

            var targets = field(effect, "effects");
            original = targets.get(effect);
            try {
                for (var invalid : List.of(List.of(), List.of(MobEffects.LUCK), List.of(MobEffects.POISON, MobEffects.WITHER))) {
                    targets.set(effect, invalid);
                    refused(admitted, api, instance, target);
                }
            } finally {
                targets.set(effect, original);
            }
            var conditions = field(definition, "conditions");
            original = conditions.get(definition);
            try {
                conditions.set(definition, List.of(new GearTypeTraitCondition(GearTypes.LEGGINGS)));
                refused(admitted, api, instance, target);
            } finally {
                conditions.set(definition, original);
            }
            var maximum = field(definition, "maxLevel");
            int level = maximum.getInt(definition);
            try {
                maximum.setInt(definition, 2);
                refused(admitted, api, instance, target);
            } finally {
                maximum.setInt(definition, level);
            }
            var conditional = TraitInstance.of(DataResource.trait("silentgear:cure_" + target), 1,
                    new GearTypeTraitCondition(GearTypes.LEGGINGS));
            refused(admitted, api, conditional, target);
            require(admitted.invoke(api, instance, target) == effect, "Restored definition was not read anew");
        }
    }

    static Field field(Object owner, String name) throws NoSuchFieldException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void refused(Method admitted, Object api, TraitInstance instance, String target) throws Exception {
        try {
            admitted.invoke(api, instance, target);
        } catch (InvocationTargetException failure) {
            require(failure.getCause() instanceof IllegalStateException, "Unexpected native admission error");
            return;
        }
        throw new AssertionError("Unsupported cure definition was admitted");
    }
}
