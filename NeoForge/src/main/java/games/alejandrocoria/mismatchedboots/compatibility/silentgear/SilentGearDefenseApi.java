package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/** Optional selected-effect binding. A exposes guarded direct controls, not production incoming dispatch. */
public final class SilentGearDefenseApi {
    private final Class<?> negate, fireproof;
    private final MethodHandle definition, effects, id, level, incoming;
    private final Field tag, scale;

    public SilentGearDefenseApi() throws ReflectiveOperationException {
        negate = nativeClass("gear.trait.effect.NegateDamageTraitEffect"); fireproof = nativeClass("gear.trait.effect.FireproofTraitEffect");
        Class<?> instance = nativeClass("api.traits.TraitInstance"), trait = nativeClass("gear.trait.Trait");
        var lookup = MethodHandles.publicLookup();
        definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(trait));
        effects = lookup.findVirtual(trait, "getEffects", MethodType.methodType(List.class));
        id = lookup.findVirtual(instance, "getTraitId", MethodType.methodType(Identifier.class));
        level = lookup.findVirtual(instance, "getLevel", MethodType.methodType(int.class));
        incoming = lookup.findVirtual(negate, "onEntityIncomingDamage", MethodType.methodType(float.class,
                ItemStack.class, int.class, LivingEntity.class, DamageSource.class, float.class, float.class));
        tag = field(negate, "damageType"); scale = field(negate, "negatedDamageScale");
    }

    public Object admittedEffect(Object instance) throws Throwable {
        String name = id.invoke(instance).toString();
        if (!Set.of("silentgear:heat_resistant", "silentgear:bounce").contains(name)) { throw new IllegalArgumentException("Unaudited defensive trait"); }
        var list = (List<?>) effects.invoke(definition.invoke(instance)); Object selected = null; int companions = 0;
        for (var effect : list) {
            if (effect.getClass() == negate && selected == null) { selected = effect; }
            else if (name.equals("silentgear:heat_resistant") && effect.getClass() == fireproof && companions++ == 0) { /* Not executed. */ }
            else { throw new IllegalStateException("Changed defensive effect count/class"); }
        }
        if (selected == null || name.equals("silentgear:heat_resistant") && companions != 1
                || !(tag.get(selected) instanceof TagKey<?> key) || !key.registry().equals(Registries.DAMAGE_TYPE)
                || !parameters(scale.getFloat(selected), (int) level.invoke(instance))) {
            throw new IllegalStateException("Unsupported defensive effect parameters");
        }
        return selected;
    }

    public float incoming(Object instance, Object effect, ItemStack context, LivingEntity actor, DamageSource source, float current, float base) throws Throwable {
        require("gear.trait.effect.NegateDamageTraitEffect", "mismatchedboots$sgNegateReady");
        require("event.GearEvents", "mismatchedboots$sgIncomingReady");
        if (effect != admittedEffect(instance) || !Float.isFinite(current) || !Float.isFinite(base) || current < 0 || base < 0
                || !Float.isFinite(base * scale.getFloat(effect) * (int) level.invoke(instance))) { throw new IllegalArgumentException("Stale effect/unsupported damage"); }
        return (float) incoming.invoke(effect, context, (int) level.invoke(instance), actor, source, current, base);
    }

    static boolean parameters(float scale, int level) { return Float.isFinite(scale) && scale >= 0 && level > 0 && level <= Byte.MAX_VALUE && Float.isFinite(scale * level); }
    public static void require(String owner, String getter) throws Throwable {
        Class<?> type = nativeClass(owner);
        if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, getter, MethodType.methodType(boolean.class)).invoke()) {
            throw new IllegalStateException("Final defensive proof unavailable: " + getter);
        }
    }
    static void requireSchedule() throws Throwable {
        Class<?> type = Class.forName("net.silentchaos512.lib.event.ServerTicks");
        if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, "mismatchedboots$sgScheduleReady", MethodType.methodType(boolean.class)).invoke()) {
            throw new IllegalStateException("Native Bounce scheduler unavailable");
        }
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Class<?> nativeClass(String name) throws ClassNotFoundException { return Class.forName("net.silentchaos512.gear." + name); }
}
