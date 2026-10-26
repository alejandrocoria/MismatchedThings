package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.List;

/** Identifier-only optional bindings. Definitions/receivers are resolved anew on every dispatch. */
final class SilentGearCureApi {
    private final Class<?> cancellation;
    private final MethodHandle definition;
    private final MethodHandle effects;
    private final MethodHandle conditions;
    private final MethodHandle instanceConditions;
    private final MethodHandle maxLevel;
    private final MethodHandle targets;
    private final MethodHandle context;
    private final MethodHandle tick;

    SilentGearCureApi() throws Throwable {
        var lookup = MethodHandles.publicLookup();
        Class<?> instance = nativeClass("api.traits.TraitInstance");
        Class<?> trait = nativeClass("gear.trait.Trait");
        Class<?> ctx = nativeClass("api.traits.TraitActionContext");
        cancellation = nativeClass("gear.trait.effect.CancelEffectsTraitEffect");
        var privateLookup = MethodHandles.privateLookupIn(cancellation, MethodHandles.lookup());
        if (!(boolean) privateLookup.findStatic(cancellation, "mismatchedboots$sgCureReady", MethodType.methodType(boolean.class)).invoke()) {
            throw new IllegalStateException("Final cancellation proof unavailable");
        }
        definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(trait));
        effects = lookup.findVirtual(trait, "getEffects", MethodType.methodType(List.class));
        conditions = lookup.findVirtual(trait, "getConditions", MethodType.methodType(List.class));
        instanceConditions = lookup.findVirtual(instance, "getConditions", MethodType.methodType(Collection.class));
        maxLevel = lookup.findVirtual(trait, "getMaxLevel", MethodType.methodType(int.class));
        targets = privateLookup.findGetter(cancellation, "effects", List.class);
        context = lookup.findConstructor(ctx, MethodType.methodType(void.class, Player.class, instance, ItemStack.class));
        tick = lookup.findVirtual(cancellation, "inventoryTick", MethodType.methodType(void.class, ctx, Level.class, Entity.class, boolean.class));
    }

    Object admitted(Object instance, String target) throws Throwable {
        Object trait = definition.invoke(instance);
        var calls = (List<?>) effects.invoke(trait);
        if ((int) maxLevel.invoke(trait) != 1 || !((List<?>) conditions.invoke(trait)).isEmpty()
                || !((Collection<?>) instanceConditions.invoke(instance)).isEmpty()
                || calls.size() != 1 || calls.getFirst().getClass() != cancellation) {
            throw new IllegalStateException("Changed cure definition");
        }

        Object effect = calls.getFirst();
        var cleared = (List<?>) targets.invoke(effect);
        if (cleared.size() != 1 || !(cleared.getFirst() instanceof Holder<?> holder)
                || !holder.is(Identifier.withDefaultNamespace(target))) {
            throw new IllegalStateException("Changed cure cancellation target");
        }

        return effect;
    }

    void tick(Object effect, Object instance, ItemStack copy, ServerLevel level, Player player) throws Throwable {
        tick.invoke(effect, context.invoke(player, instance, copy), level, player, true);
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + name);
    }
}
