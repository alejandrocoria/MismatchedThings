package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Feasibility only: no production readiness/guard contract or full native trait dispatch. */
public final class SilentGearRemainingSpike {
    public static volatile boolean enabled;
    public static int queries, pushes, cureTicks;
    public static final Map<String, Integer> cureCalls = new HashMap<>(); // Integrated-server thread only.
    private SilentGearRemainingSpike() {}

    public static boolean voidWard(Player player) {
        return enabled && has(player.getItemBySlot(EquipmentSlot.FEET), "void_ward");
    }

    public static boolean snow(ItemStack root) { return enabled && has(root, "snow_walker"); }

    private static boolean has(ItemStack root, String id) {
        return SilentGearCompatibility.effectiveTraits(root).stream().anyMatch(trait -> trait.id().toString().equals("silentgear:" + id));
    }

    public static void cures(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!enabled || slot == null || !(entity instanceof Player player)) { return; }
        try {
            boolean counted = false;
            for (var trait : SilentGearCompatibility.effectiveTraits(root)) {
                String name = trait.id().getPath();
                if (!List.of("cure_poison", "cure_wither").contains(name)) { continue; }
                Object definition = SilentGearTraitSpike.traitOf(trait.nativeInstance());
                var effects = (List<?>) definition.getClass().getMethod("getEffects").invoke(definition);
                if (effects.size() != 1 || !effects.getFirst().getClass().getSimpleName().equals("CancelEffectsTraitEffect")) { throw new AssertionError("Changed native cure definition"); }
                Object effect = effects.getFirst();
                var field = effect.getClass().getDeclaredField("effects"); field.setAccessible(true);
                var targets = (List<?>) field.get(effect);
                if (targets.size() != 1 || !(targets.getFirst() instanceof Holder<?> target)
                        || !target.is(net.minecraft.resources.Identifier.withDefaultNamespace(name.equals("cure_poison") ? "poison" : "wither"))) {
                    throw new AssertionError("Changed native cancellation target");
                }
                Object context = SilentGearTraitSpike.context(player, trait.nativeInstance(), root.copy());
                effect.getClass().getMethod("inventoryTick", context.getClass(), Level.class, Entity.class, boolean.class)
                        .invoke(effect, context, level, player, true);
                cureCalls.merge(name, 1, Integer::sum); counted = true;
            }
            if (counted) { cureTicks++; }
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    /** Explicit synthetic computed-trait acquisition, not a native material/part recipe. */
    @SuppressWarnings("unchecked")
    static ItemStack injected(NeoForgeSilentGearTest.NativeApi api, String... names) throws Exception {
        ItemStack source = api.boot("iron");
        Object props = source.get(api.properties);
        var values = new HashMap<>((Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        var instances = new java.util.ArrayList<Object>();
        Class<?> trait = SilentGearTraitSpike.nativeClass("gear.trait.Trait"), instance = SilentGearTraitSpike.nativeClass("api.traits.TraitInstance");
        for (String name : names) {
            Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:" + name);
            Object definition = ((Supplier<?>) ref).get();
            instances.add(instance.getMethod("of", trait, int.class, java.util.Collection.class).invoke(null, definition, 1, List.of()));
        }
        Object key = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        values.put(key, SilentGearTraitSpike.nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(instances));
        source.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values));
        for (String name : names) { if (api.traitLevel(source, name) != 1) { throw new AssertionError("Invalid injected native trait: " + name); } }
        return source;
    }
}
