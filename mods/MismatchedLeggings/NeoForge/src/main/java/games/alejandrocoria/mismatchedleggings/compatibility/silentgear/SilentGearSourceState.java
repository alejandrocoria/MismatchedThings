package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility.component;
import static games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility.nativeClass;

/** Reads computed native phases on disposable copies. Does not run any native effect or tick. */
final class SilentGearSourceState {
    private final Class<?> instance = nativeClass("api.traits.TraitInstance");
    private final Class<?> oxidation = nativeClass("gear.trait.effect.OxidationTraitEffect");
    private final Method traits = nativeClass("util.TraitHelper").getMethod("getTraits", ItemInstance.class);
    private final Method id = instance.getMethod("getTraitId");
    private final Method level = instance.getMethod("getLevel");
    private final Method definition = instance.getMethod("getTrait");
    private final Method maxLevel = nativeClass("gear.trait.Trait").getMethod("getMaxLevel");
    private final Method effects = nativeClass("gear.trait.Trait").getMethod("getEffects");
    private final Method resource = nativeClass("api.util.DataResource").getMethod("trait", String.class);
    private final Method construction = nativeClass("util.GearData").getMethod("getConstruction", ItemInstance.class);
    private final Method parts = nativeClass("core.component.GearConstructionData").getMethod("parts");
    private final Method context = nativeClass("api.property.ComputeContext").getMethod("gear", ItemStack.class, List.class);
    private final Method conditions = instance.getMethod("conditionsMatch", nativeClass("api.property.ComputeContext"));
    private final Field times = oxidation.getDeclaredField("counterTimesPerLevel");
    private final DataComponentType<Object> stage = component("OXIDATION_STAGE");
    private final DataComponentType<Object> counter = component("OXIDATION_COUNTER");

    SilentGearSourceState() throws ReflectiveOperationException {
        times.setAccessible(true);
        if (stage == null || counter == null || stage.codec() == null || counter.codec() == null
                || !(stage.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0)).getOrThrow() instanceof Byte)
                || !(counter.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0)).getOrThrow() instanceof Integer)) {
            throw new IllegalStateException("Unsupported native SG oxidation component schema");
        }
    }

    Optional<SilentGearState.Oxidation> seed(ItemStack source) throws ReflectiveOperationException {
        if (!SilentGearCompatibility.leggings(source)) {
            return Optional.empty();
        }
        var copy = source.copy();
        var phases = phases(copy);
        if (phases.isEmpty()) {
            return Optional.empty();
        }
        Object rawStage = copy.getOrDefault(stage, (byte) 0);
        Object rawCounter = copy.getOrDefault(counter, 0);
        if (phases.size() != 1 || !(rawStage instanceof Byte step) || !(rawCounter instanceof Integer count)) {
            throw new IllegalArgumentException("Ambiguous or malformed native SG oxidation state");
        }
        Object trait = phases.getFirst();
        var lane = new SilentGearState.Oxidation(((Identifier) id.invoke(trait)).getPath(), (int) level.invoke(trait), step, count);
        validateDefinition(definition.invoke(trait), lane);
        return Optional.of(lane);
    }

    Optional<SilentGearState.Oxidation> preserve(ItemStack source, Optional<SilentGearState.Oxidation> live)
            throws ReflectiveOperationException {
        // Source inequality (trim/enchantment edits) is not a clock reset.
        if (!SilentGearCompatibility.leggings(source) || phases(source.copy()).isEmpty()) {
            return Optional.empty();
        }
        if (live.isPresent()) {
            Object trait = ((Supplier<?>) resource.invoke(null, "silentgear:" + live.get().phase())).get();
            validateDefinition(trait, live.get());
        }
        return live;
    }

    private List<?> phases(ItemStack source) throws ReflectiveOperationException {
        var found = (List<?>) traits.invoke(null, source);
        if (found.size() > 128) {
            throw new IllegalArgumentException("Oversized native SG trait view");
        }
        var result = new java.util.ArrayList<Object>();
        Object computedContext = context.invoke(null, source, parts.invoke(construction.invoke(null, source)));
        for (Object trait : found) {
            var name = (Identifier) id.invoke(trait);
            if ((name.equals(Identifier.parse("silentgear:polished")) || name.equals(Identifier.parse("silentgear:oxidized")))
                    && (boolean) conditions.invoke(trait, computedContext)) {
                result.add(trait);
            }
        }
        if (result.size() > 1) {
            throw new IllegalArgumentException("Ambiguous native SG oxidation phase");
        }
        return result;
    }

    private void validateDefinition(Object trait, SilentGearState.Oxidation lane) throws ReflectiveOperationException {
        int limit = (int) maxLevel.invoke(trait);
        var nativeEffects = ((List<?>) effects.invoke(trait)).stream().filter(oxidation::isInstance).toList();
        if (nativeEffects.size() != 1 || limit < 1 || limit > Byte.MAX_VALUE || lane.stage() > limit || lane.level() > limit) {
            throw new IllegalArgumentException("Unsupported native SG oxidation definition/stage");
        }
        var thresholds = (List<?>) times.get(nativeEffects.getFirst());
        if (thresholds.isEmpty() || thresholds.size() > 100
                || thresholds.stream().anyMatch(value -> !(value instanceof Integer time) || time < 1 || time == Integer.MAX_VALUE)
                || lane.level() > thresholds.size() || lane.counter() > (int) thresholds.get(lane.level() - 1)) {
            throw new IllegalArgumentException("Invalid native SG oxidation threshold/counter");
        }
    }
}
