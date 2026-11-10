package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.TreeMap;

import static games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility.nativeClass;

/** Computed, condition-resolved source instances; highest level once in stable ID order. */
final class SilentGearEffectSources {
    private final Class<?> instance = nativeClass("api.traits.TraitInstance");
    private final Method traits = nativeClass("util.TraitHelper").getMethod("getTraits", ItemInstance.class);
    private final Method id = instance.getMethod("getTraitId");
    private final Method level = instance.getMethod("getLevel");
    private final Method construction = nativeClass("util.GearData").getMethod("getConstruction", ItemInstance.class);
    private final Method parts = nativeClass("core.component.GearConstructionData").getMethod("parts");
    private final Method context = nativeClass("api.property.ComputeContext").getMethod("gear", ItemStack.class, List.class);
    private final Method conditions = instance.getMethod("conditionsMatch", nativeClass("api.property.ComputeContext"));

    SilentGearEffectSources() throws ReflectiveOperationException {}

    List<Trait> traits(ItemStack root) throws ReflectiveOperationException {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var merged = new TreeMap<Identifier, Trait>();
        for (var source : List.of(data.left().source(), data.right().source())) {
            for (var trait : sourceTraits(source)) {
                merged.merge(trait.id(), trait, (a, b) -> a.level() >= b.level() ? a : b);
            }
        }
        return List.copyOf(merged.values());
    }

    List<Trait> sourceTraits(ItemStack source) throws ReflectiveOperationException {
        if (!SilentGearCompatibility.leggings(source)) { return List.of(); }
        var result = new java.util.ArrayList<Trait>();
        var copy = source.copy();
        var found = (List<?>) traits.invoke(null, copy);
        if (found.size() > 128) { throw new IllegalArgumentException("Oversized native trait view"); }
        var computed = context.invoke(null, copy, parts.invoke(construction.invoke(null, copy)));
        for (var value : found) {
            int resolvedLevel = (int) level.invoke(value);
            if (resolvedLevel < 1 || resolvedLevel > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid native trait level");
            }
            if ((boolean) conditions.invoke(value, computed)) {
                result.add(new Trait((Identifier) id.invoke(value), resolvedLevel, value));
            }
        }
        return List.copyOf(result);
    }

    record Trait(Identifier id, int level, Object nativeInstance) {}
}
