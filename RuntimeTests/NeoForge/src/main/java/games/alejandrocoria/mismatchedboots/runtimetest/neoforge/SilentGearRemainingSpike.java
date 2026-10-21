package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Independent production witnesses and explicitly synthetic cure acquisition. No gameplay adapter. */
public final class SilentGearRemainingSpike {
    public static int queries, pushes, cureTicks;
    public static final Map<String, Integer> cureCalls = new HashMap<>(); // Integrated-server thread only.
    private SilentGearRemainingSpike() {}

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
