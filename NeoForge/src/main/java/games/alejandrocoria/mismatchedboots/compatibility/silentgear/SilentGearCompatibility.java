package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Native source reads and lifecycle ownership; live copy computations belong to SilentGearLiveState. */
public final class SilentGearCompatibility {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final Supplier<DataComponentType<SilentGearState>> STATE = TYPES.register("silent_gear_state", () ->
            DataComponentType.<SilentGearState>builder().persistent(SilentGearState.CODEC)
                             .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SilentGearState.CODEC)).build());
    private static volatile Api api;

    private SilentGearCompatibility() {
    }

    public static DataComponentType<SilentGearState> state() {
        return STATE.get();
    }

    static Api binding() {
        return api;
    }

    /** Shared JDT groups must not reclaim a recorded root override through later numerical coincidence. */
    public static boolean overrides(ItemStack root, Identifier attribute, Identifier modifier) {
        var state = root.get(state());
        return state != null && state.overrides().stream().anyMatch(key -> key.attribute().equals(attribute) && key.modifier().equals(modifier));
    }

    static ItemStack carrier(ItemStack root) {
        Api binding = api;
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (binding == null || data == null) {
            return ItemStack.EMPTY;
        }
        ItemStack left = data.left().source(), right = data.right().source();
        return binding.boot(left) ? left : binding.boot(right) ? right : ItemStack.EMPTY;
    }

    public static void initialize(IEventBus bus) {
        TYPES.register(bus); // Decode our saved state without loading optional native classes.
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearCompatibility::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            api = new Api();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Silent Gear source API; mixed SG state disabled", failure);
        }
    }

    public static ItemAttributeModifiers normalize(ItemStack source, ItemAttributeModifiers attributes) {
        Api binding = api;
        return binding != null && binding.boot(source) ? normalize(attributes) : attributes;
    }

    static ItemAttributeModifiers normalize(ItemAttributeModifiers attributes) {
        return new ItemAttributeModifiers(attributes.modifiers().stream().distinct().toList());
    }

    /** Actual computed instances, stable ID order, highest level once. No native identity override. */
    public static List<Trait> effectiveTraits(ItemStack root) {
        Api binding = api;
        if (binding == null) {
            return List.of();
        }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        var equippable = root.get(DataComponents.EQUIPPABLE);
        if (root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem) || data == null
                || equippable == null || equippable.slot() != EquipmentSlot.FEET) {
            return List.of();
        }
        var merged = new TreeMap<Identifier, Trait>();
        try {
            for (ItemStack source : List.of(data.left().source(), data.right().source())) {
                for (Trait trait : binding.traits(source)) {
                    merged.merge(trait.id, trait, (first, second) -> first.level >= second.level ? first : second);
                }
            }
        } catch (IllegalStateException | LinkageError failure) {
            disable(binding, failure);
            return List.of();
        }
        return List.copyOf(merged.values());
    }

    public static void prepare(ItemStack previous, ItemStack output, AttributeRuleSet policies) {
        SilentGearState old = previous == null ? null : previous.get(state());
        output.remove(state()); // Opaque source projection must never initialize this component.
        Api binding = api;
        if (binding == null) {
            retain(previous, output, old);
            return;
        }
        try {
            prepare(previous, output, policies, old, binding);
        } catch (IllegalStateException | LinkageError failure) {
            disable(binding, failure);
            retain(previous, output, old);
        }
    }

    private static void prepare(ItemStack previous, ItemStack output, AttributeRuleSet policies, SilentGearState old, Api binding) {
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        ItemStack left = data.left().source(), right = data.right().source();
        if (!binding.boot(left) && !binding.boot(right)) {
            return;
        }
        if (previous != null && old == null) {
            return;
        } // No migration/reseed of absent live state.

        Optional<SilentGearState.Oxidation> a = SilentGearLiveState.seed(left, binding), b = SilentGearLiveState.seed(right, binding);
        var keys = new LinkedHashSet<SilentGearState.Key>();
        for (ItemStack source : List.of(left, right)) {
            if (binding.boot(source)) {
                Services.PLATFORM.getCompositionAttributeModifiers(source).modifiers()
                                 .forEach(entry -> keys.add(SilentGearState.Key.of(entry)));
            }
        }
        var baseline = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<SilentGearState.Key> overrides = List.of();
        if (old != null) {
            var originals = ModContent.getMixedBootsData(previous).orElseThrow();
            a = preserveLane(old.left(), originals.left().source(), left, a, binding);
            b = preserveLane(old.right(), originals.right().source(), right, b, binding);
            old.projection().modifiers().forEach(entry -> keys.add(SilentGearState.Key.of(entry)));
            keys.addAll(old.overrides());
            var changed = new LinkedHashSet<>(old.overrides());
            var current = previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            for (var key : keys) {
                if (!entries(current, key).equals(entries(old.projection(), key))) {
                    changed.add(key);
                }
            }
            overrides = List.copyOf(changed);
            var result = new ArrayList<>(baseline.modifiers());
            for (var key : overrides) {
                result.removeIf(entry -> SilentGearState.Key.of(entry).equals(key));
                result.addAll(entries(current, key));
            }
            // Recomposition rebuilds source attributes. Keep root-only foreign groups, not historical
            // source groups that legitimately disappeared through the source transformation.
            var historical = new LinkedHashSet<SilentGearState.Key>();
            for (ItemStack source : List.of(originals.left().source(), originals.right().source())) {
                Services.PLATFORM.getCompositionAttributeModifiers(source)
                                 .modifiers()
                                 .forEach(entry -> historical.add(SilentGearState.Key.of(entry)));
            }
            historical.addAll(keys);
            for (var key : current.modifiers().stream().map(SilentGearState.Key::of).distinct().toList()) {
                if (historical.contains(key)) {
                    continue;
                }
                result.removeIf(entry -> SilentGearState.Key.of(entry).equals(key));
                result.addAll(entries(current, key));
            }
            baseline = new ItemAttributeModifiers(List.copyOf(result));
        }
        var rules = keys.stream().map(SilentGearState.Key::attribute).distinct().sorted()
                        .map(id -> new SilentGearState.Rule(id, policies.policyFor(id))).toList();
        var projection = new ItemAttributeModifiers(output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                                                          .modifiers()
                                                          .stream()
                                                          .filter(entry -> keys.contains(SilentGearState.Key.of(entry)))
                                                          .toList());
        var next = new SilentGearState(a, b, rules, projection, overrides);
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, baseline);
        output.set(state(), next);
        SilentGearLiveState.prepare(output);
    }

    private static void retain(ItemStack previous, ItemStack output, SilentGearState old) {
        if (old == null) {
            return;
        }
        var result = new ArrayList<>(output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers());
        var keys = new LinkedHashSet<>(old.overrides());
        old.projection().modifiers().forEach(entry -> keys.add(SilentGearState.Key.of(entry)));
        var current = previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var key : keys) {
            result.removeIf(entry -> SilentGearState.Key.of(entry).equals(key));
            result.addAll(entries(current, key));
        }
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(result)));
        output.set(state(), old);
    }

    private static synchronized void disable(Api binding, Throwable failure) {
        if (api == binding) {
            api = null;
            Constants.LOG.warn("Could not read Silent Gear source API; mixed SG state disabled", failure);
        }
    }

    private static Optional<SilentGearState.Oxidation> preserveLane(Optional<SilentGearState.Oxidation> live,
                                                                    ItemStack previous,
                                                                    ItemStack replacement,
                                                                    Optional<SilentGearState.Oxidation> seed,
                                                                    Api binding) {
        if (seed.isEmpty()) {
            return Optional.empty();
        }
        if (binding.oxidation(previous).isEmpty()) {
            return seed;
        }
        if (previous.getItem() != replacement.getItem()
                || !java.util.Objects.equals(previous.get(binding.construction), replacement.get(binding.construction))) {
            throw new IllegalArgumentException("Unsupported SG construction replacement; oxidation reset is not defined");
        }
        live.ifPresent(value -> binding.validateLane(replacement, value));
        return live;
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers attributes, SilentGearState.Key key) {
        return attributes.modifiers().stream().filter(entry -> SilentGearState.Key.of(entry).equals(key)).toList();
    }

    public record Trait(Identifier id, int level, Object nativeInstance) {
    }

    static final class Api {
        private final Class<?> armor = nativeClass("api.item.GearArmor");
        private final Class<?> instance = nativeClass("api.traits.TraitInstance");
        private final Class<?> trait = nativeClass("gear.trait.Trait");
        private final Class<?> oxidation = nativeClass("gear.trait.effect.OxidationTraitEffect");
        private final MethodHandle traits, id, level, definition, maxLevel, effects, times;
        private final DataComponentType<Object> construction = component("GEAR_CONSTRUCTION");
        final DataComponentType<Object> stage = component("OXIDATION_STAGE"), counter = component("OXIDATION_COUNTER");

        private Api() throws ReflectiveOperationException {
            if (construction == null || stage == null || counter == null || stage.codec() == null || counter.codec() == null
                    || !(stage.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0)).getOrThrow() instanceof Byte)
                    || !(counter.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0)).getOrThrow() instanceof Integer)) {
                throw new IllegalStateException("Unsupported native SG oxidation component schema");
            }
            var lookup = MethodHandles.publicLookup();
            traits = lookup.findStatic(nativeClass("util.TraitHelper"), "getTraits",
                                       MethodType.methodType(List.class, net.minecraft.world.item.ItemInstance.class));
            id = lookup.findVirtual(instance, "getTraitId", MethodType.methodType(Identifier.class));
            level = lookup.findVirtual(instance, "getLevel", MethodType.methodType(int.class));
            definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(trait));
            maxLevel = lookup.findVirtual(trait, "getMaxLevel", MethodType.methodType(int.class));
            effects = lookup.findVirtual(trait, "getEffects", MethodType.methodType(List.class));
            times = MethodHandles.privateLookupIn(oxidation, MethodHandles.lookup())
                                 .findGetter(oxidation, "counterTimesPerLevel", List.class);
        }

        boolean boot(ItemStack stack) {
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            return armor.isInstance(stack.getItem()) && equippable != null && equippable.slot() == EquipmentSlot.FEET;
        }

        List<Trait> traits(ItemStack stack) {
            if (!boot(stack)) {
                return List.of();
            }
            try {
                var result = new ArrayList<Trait>();
                for (Object value : (List<?>) traits.invoke(stack.copy())) {
                    result.add(new Trait((Identifier) id.invoke(value), (int) level.invoke(value), value));
                }
                if (result.size() > 128 || result.stream().anyMatch(value -> value.level < 1 || value.level > Byte.MAX_VALUE)) {
                    throw new IllegalArgumentException("Invalid bounded native SG trait view");
                }
                return List.copyOf(result);
            } catch (IllegalArgumentException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Could not read native SG traits", failure);
            }
        }

        Optional<SilentGearState.Oxidation> oxidation(ItemStack stack) {
            var eligible = traits(stack).stream().filter(value -> value.id.equals(Identifier.fromNamespaceAndPath("silentgear", "polished"))
                    || value.id.equals(Identifier.fromNamespaceAndPath("silentgear", "oxidized"))).toList();
            if (eligible.isEmpty()) {
                return Optional.empty();
            }
            if (eligible.size() != 1) {
                throw new IllegalArgumentException("Ambiguous native SG oxidation phase");
            }
            Object rawStage = stack.getOrDefault(stage, (byte) 0), rawCounter = stack.getOrDefault(counter, 0);
            if (!(rawStage instanceof Byte value) || !(rawCounter instanceof Integer count)) {
                throw new IllegalArgumentException("Invalid native SG oxidation component types");
            }
            var lane = new SilentGearState.Oxidation(eligible.getFirst().id.getPath(), eligible.getFirst().level, value, count);
            validateLane(stack, lane);
            return Optional.of(lane);
        }

        void validateLane(ItemStack source, SilentGearState.Oxidation lane) {
            try {
                if (traits(source).stream().noneMatch(item -> List.of("silentgear:polished", "silentgear:oxidized").contains(item.id.toString()))) {
                    throw new IllegalArgumentException("Source has no native oxidation phase");
                }
                Object resource = nativeClass("api.util.DataResource").getMethod("trait", String.class).invoke(null, "silentgear:" + lane.phase());
                Object nativeTrait = ((Supplier<?>) resource).get();
                int limit = (int) maxLevel.invoke(nativeTrait);
                var nativeEffects = ((List<?>) effects.invoke(nativeTrait)).stream().filter(oxidation::isInstance).toList();
                if (nativeEffects.size() != 1 || limit < 1 || limit > Byte.MAX_VALUE || lane.stage() > limit || lane.level() > limit) {
                    throw new IllegalArgumentException("Unsupported native SG oxidation definition/stage");
                }
                var thresholds = (List<?>) times.invoke(nativeEffects.getFirst());
                if (thresholds.isEmpty() || thresholds.size() > 100 || thresholds.stream().anyMatch(item ->
                                                                                                            !(item instanceof Integer time) || time < 1 || time == Integer.MAX_VALUE)) {
                    throw new IllegalArgumentException("Invalid native SG oxidation thresholds");
                }
                int index = lane.level() - 1;
                if (index >= thresholds.size()) {
                    throw new IllegalArgumentException("Unsupported native SG oxidation level/threshold");
                }
                if (lane.counter() > (int) thresholds.get(index)) {
                    throw new IllegalArgumentException("Invalid native SG oxidation counter");
                }
            } catch (IllegalArgumentException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Could not validate native SG oxidation", failure);
            }
        }
    }

    private static Class<?> nativeClass(String path) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + path);
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(String name) throws ReflectiveOperationException {
        return (DataComponentType<Object>) ((Supplier<?>) nativeClass("setup.SgDataComponents").getField(name).get(null)).get();
    }
}
