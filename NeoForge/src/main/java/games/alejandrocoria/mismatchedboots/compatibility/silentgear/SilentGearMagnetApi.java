package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Narrow optional native magnet binding. No item tick routing or Wielder permission is installed here. */
public final class SilentGearMagnetApi {
    public static final Set<String> IDS = Set.of("silentgear:magnetic", "silentgear:greedy");
    private final Class<?> magnet;
    private final MethodHandle context, definition, id, level, effects, tick;
    private final Field strength, range, affected;
    private final SilentGearWearerApi.Carriers carriers = new SilentGearWearerApi.Carriers();

    public SilentGearMagnetApi() throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        magnet = nativeClass("gear.trait.effect.ItemMagnetTraitEffect");
        Class<?> instance = nativeClass("api.traits.TraitInstance"), ctx = nativeClass("api.traits.TraitActionContext"), trait = nativeClass("gear.trait.Trait");
        context = lookup.findConstructor(ctx, MethodType.methodType(void.class, Player.class, instance, ItemStack.class));
        definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(trait));
        id = lookup.findVirtual(instance, "getTraitId", MethodType.methodType(Identifier.class));
        level = lookup.findVirtual(instance, "getLevel", MethodType.methodType(int.class));
        effects = lookup.findVirtual(trait, "getEffects", MethodType.methodType(List.class));
        tick = lookup.findVirtual(magnet, "inventoryTick", MethodType.methodType(void.class, ctx, Level.class, Entity.class, boolean.class));
        strength = field("pullStrength"); range = field("effectRange"); affected = field("affectedItems");
    }

    public void requireProof() throws Throwable {
        for (var gate : List.of(new String[]{"gear.trait.effect.ItemMagnetTraitEffect", "mismatchedboots$sgMagnetReady"},
                new String[]{"gear.util.MagnetPullTracker", "mismatchedboots$sgMagnetQueueReady"})) {
            var type = nativeClass(gate[0]);
            if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, gate[1], MethodType.methodType(boolean.class)).invoke()) {
                throw new IllegalStateException("Final native magnet proof unavailable: " + gate[0]);
            }
        }
    }

    public ItemStack carrier(ItemStack root) throws Throwable { return carriers.carrier(root); }

    public Object admittedEffect(Object instance) throws Throwable {
        if (!IDS.contains(id.invoke(instance).toString())) { throw new IllegalArgumentException("Unaudited magnet trait"); }
        var matches = ((List<?>) effects.invoke(definition.invoke(instance))).stream().filter(magnet::isInstance).toList();
        if (matches.size() != 1 || matches.getFirst().getClass() != magnet) { throw new IllegalStateException("Changed native magnet effect count/class"); }
        Object effect = matches.getFirst();
        if (!parameters(strength.getFloat(effect), range.getFloat(effect), (int) level.invoke(instance))
                || !(affected.get(effect) instanceof Optional<?> filter) || filter.isPresent() && !(filter.get() instanceof Ingredient)) {
            throw new IllegalStateException("Unsupported native magnet parameters/filter");
        }
        return effect;
    }

    public void tick(Object effect, Player player, Object instance, ItemStack carrier, boolean selected) throws Throwable {
        requireProof();
        if (effect != admittedEffect(instance)) { throw new IllegalArgumentException("Stale/foreign magnet definition"); }
        if (player == null || player.level().isClientSide() || !selected) { return; }
        invoke(effect, context.invoke(player, instance, carrier), player);
    }

    // The test-only counter observes this mod-owned invocation boundary, never modifies admitted native code.
    private void invoke(Object effect, Object ctx, Player player) throws Throwable { tick.invoke(effect, ctx, player.level(), player, true); }

    static boolean parameters(float strength, float range, int level) {
        return Float.isFinite(strength) && Float.isFinite(range) && level > 0 && level <= Byte.MAX_VALUE
                && Float.isFinite(1 + range * level);
    }
    private Field field(String name) throws NoSuchFieldException { var field = magnet.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Class<?> nativeClass(String name) throws ClassNotFoundException { return Class.forName("net.silentchaos512.gear." + name); }
}
