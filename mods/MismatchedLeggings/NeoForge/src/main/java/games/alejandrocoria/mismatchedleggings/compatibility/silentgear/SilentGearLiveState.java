package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireAttributeProjections;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireAttributes;
import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility.component;
import static games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility.nativeClass;

/** Disposable native work followed by one validated publication of both lanes and the armor group. */
public final class SilentGearLiveState {
    private static final NeoForgeCompositionAttributes ATTRIBUTES = new NeoForgeCompositionAttributes();
    private static Native api;
    private static boolean disabled;

    private SilentGearLiveState() {}

    static ItemStack settledSource(ItemStack source) throws ReflectiveOperationException {
        if (!SilentGearCompatibility.leggings(source)) { return source; }
        var copy = source.copy();
        var nativeApi = nativeApi();
        nativeApi.profile(copy, nativeApi.lanes.seed(copy));
        nativeApi.settle(copy, null);
        return copy;
    }

    static Prepared prepare(ItemStack root, MixedLeggingsData data, SilentGearState state) throws ReflectiveOperationException {
        if (disabled) {
            var nativeApi = nativeApi();
            if (state.left().isPresent() || state.right().isPresent()
                    || nativeApi.profile(data.left().source(), state.left()).dynamic()
                    || nativeApi.profile(data.right().source(), state.right()).dynamic()) {
                throw new IllegalStateException("SG live state contract previously refused");
            }
            return new Prepared(root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY), state);
        }
        return calculate(root, data, state, null, null, null, true);
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (disabled || !SilentGearCompatibility.available()) { return; }
        var old = root.get(SilentGearCompatibility.state());
        if (old == null) { return; }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) { return; }
        try {
            var next = calculate(root, data, old, level, entity, slot, false);
            publish(root, next);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disabled = true;
            LeggingsContent.LOG.warn("Silent Gear mixed leggings live state disabled: projection/oxidation contract unavailable", failure);
        }
    }

    static void publish(ItemStack root, Prepared next) {
        if (!next.attributes().equals(root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY))) {
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, next.attributes());
        }
        if (!next.state().equals(root.get(SilentGearCompatibility.state()))) {
            root.set(SilentGearCompatibility.state(), next.state());
        }
        if (next.justDire() != null) {
            root.set(JustDireAttributes.projections(), next.justDire());
        }
    }

    private static Prepared calculate(ItemStack root, MixedLeggingsData data, SilentGearState old,
                                      ServerLevel level, Entity entity, EquipmentSlot slot, boolean initial)
            throws ReflectiveOperationException {
        var nativeApi = nativeApi();
        var left = data.left().source();
        var right = data.right().source();
        // Validate BOTH definitions/lanes before executing either callback, including after a reload.
        var a = nativeApi.profile(left, old.left());
        var b = nativeApi.profile(right, old.right());
        var leftLane = old.left();
        var rightLane = old.right();
        if (!initial && slot == null && entity.tickCount % 20 == 0) {
            leftLane = nativeApi.advance(left, leftLane, level, entity);
            rightLane = nativeApi.advance(right, rightLane, level, entity);
        }
        if (phaseChanged(old.left(), leftLane)) { a = nativeApi.profile(left, leftLane); }
        if (phaseChanged(old.right(), rightLane)) { b = nativeApi.profile(right, rightLane); }
        boolean dynamic = a.dynamic() || b.dynamic();
        Optional<SilentGearState.Sample> sample = dynamic ? Optional.of(new SilentGearState.Sample(
                root.getDamageValue(), root.getMaxDamage(), digest(a.inputs() + "|" + b.inputs()))) : Optional.empty();
        boolean refresh = initial || !sample.equals(old.sample()) || phaseChanged(old.left(), leftLane) || phaseChanged(old.right(), rightLane);
        var current = root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        boolean overridden = old.armorOverridden() || !SilentGearState.Projection.capture(current).equals(old.armorProjection());
        var attributes = current;
        var projection = old.armorProjection();
        JustDireAttributeProjections justDire = null;
        if (refresh && dynamic && !overridden) {
            var first = nativeApi.attributes(left, root, leftLane, a.dynamic());
            var second = nativeApi.attributes(right, root, rightLane, b.dynamic());
            var calculated = AttributeMerger.merge(first, second, id -> old.armorPolicy()).primary();
            justDire = JustDireAttributes.withSourceArmor(
                    root, data, old.armorPolicy(), first, second);
            if (justDire != null) { calculated = justDire.states().getFirst(); }
            projection = SilentGearState.Projection.capture(calculated);
            attributes = replace(current, calculated);
        }
        var state = new SilentGearState(leftLane, rightLane, old.armorPolicy(), projection, overridden, sample);
        return new Prepared(attributes, state, justDire);
    }

    private static boolean phaseChanged(Optional<SilentGearState.Oxidation> before, Optional<SilentGearState.Oxidation> after) {
        return !before.map(value -> List.of(value.phase(), value.level(), value.stage()))
                .equals(after.map(value -> List.of(value.phase(), value.level(), value.stage())));
    }

    static ItemAttributeModifiers replace(ItemAttributeModifiers current, ItemAttributeModifiers calculated) {
        var entries = new ArrayList<>(current.modifiers().stream().filter(entry -> !SilentGearState.managed(entry)).toList());
        entries.addAll(calculated.modifiers().stream().filter(SilentGearState::managed).toList());
        return new ItemAttributeModifiers(entries);
    }

    private static ItemAttributeModifiers armor(ItemAttributeModifiers attributes) {
        return new ItemAttributeModifiers(attributes.modifiers().stream().filter(SilentGearState::managed).toList());
    }

    private static String armorSample(ItemAttributeModifiers attributes) {
        // Holder/display toString values can contain JVM identities. Persist only the managed fields.
        return jsonSample(SilentGearState.Projection.CODEC.listOf().encodeStart(JsonOps.INSTANCE,
                SilentGearState.Projection.capture(attributes)).getOrThrow());
    }

    private static String jsonSample(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            return value.getAsJsonObject().keySet().stream().sorted()
                    .map(key -> new com.google.gson.JsonPrimitive(key) + ":" + jsonSample(value.getAsJsonObject().get(key)))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value.isJsonArray()) {
            return value.getAsJsonArray().asList().stream().map(SilentGearLiveState::jsonSample)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        return value.toString();
    }

    private static String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Native nativeApi() throws ReflectiveOperationException {
        if (api == null) { api = new Native(); }
        return api;
    }

    record Prepared(ItemAttributeModifiers attributes, SilentGearState state,
                    JustDireAttributeProjections justDire) {
        Prepared(ItemAttributeModifiers attributes, SilentGearState state) { this(attributes, state, null); }
    }
    private record Profile(boolean dynamic, String inputs) {}

    private static final class Native {
        private final SilentGearSourceState lanes = new SilentGearSourceState();
        private final SilentGearEffectSources sources = new SilentGearEffectSources();
        private final Class<?> trait = nativeClass("gear.trait.Trait");
        private final Class<?> instance = nativeClass("api.traits.TraitInstance");
        private final Class<?> oxidation = nativeClass("gear.trait.effect.OxidationTraitEffect");
        private final Class<?> number = nativeClass("gear.trait.effect.NumberPropertyModifierTraitEffect");
        private final Method recalculate = nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class);
        private final Method effects = trait.getMethod("getEffects");
        private final Method definition = instance.getMethod("getTrait");
        private final Method maxLevel = trait.getMethod("getMaxLevel");
        private final Method of = instance.getMethod("of", trait, int.class, Collection.class);
        private final Method reference = nativeClass("api.util.DataResource").getMethod("trait", String.class);
        private final Constructor<?> context = nativeClass("api.traits.TraitActionContext").getConstructor(Player.class, instance, ItemStack.class);
        private final Constructor<?> properties = nativeClass("core.component.GearPropertiesData").getConstructor(Map.class);
        private final Method propertyMap = nativeClass("core.component.GearPropertiesData").getMethod("properties");
        private final Constructor<?> traitList = nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class);
        private final Method tick = oxidation.getMethod("inventoryTick", context.getDeclaringClass(), Level.class, Entity.class, boolean.class);
        private final Method hasPart = nativeClass("util.GearData").getMethod("hasPartOfType", ItemInstance.class, nativeClass("api.part.PartType"));
        private final Object coating = ((Supplier<?>) nativeClass("setup.gear.PartTypes").getField("COATING").get(null)).get();
        private final Field mods = field(number, "mods");
        private final Field next = field(oxidation, "nextOxidationTrait");
        private final Field times = field(oxidation, "counterTimesPerLevel");
        private final Method multiplier = nativeClass("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplier");
        private final Method damageRatio = nativeClass("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplyDamageRatio");
        private final Method originalValue = nativeClass("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplyOriginalValue");
        private final Object traitKey = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        private final Object armor = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("ARMOR").get(null)).get();
        private final Object attack = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("ATTACK_DAMAGE").get(null)).get();
        private final Object speed = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("HARVEST_SPEED").get(null)).get();
        private final DataComponentType<Object> propertyComponent = component("GEAR_PROPERTIES");
        private final DataComponentType<Object> stage = component("OXIDATION_STAGE");
        private final DataComponentType<Object> counter = component("OXIDATION_COUNTER");
        private final DataComponentType<Object> flag = component("RECALCULATE_FLAG");
        @SuppressWarnings("unchecked")
        private final Codec<Object> traitCodec = (Codec<Object>) trait.getField("CODEC").get(null);

        private Native() throws ReflectiveOperationException {}

        private Profile profile(ItemStack source, Optional<SilentGearState.Oxidation> lane) throws ReflectiveOperationException {
            if (!SilentGearCompatibility.leggings(source)) {
                if (lane.isPresent()) { throw new IllegalStateException("Oxidation lane lost native source identity"); }
                return new Profile(false, armorSample(ATTRIBUTES.query(source)));
            }
            boolean dynamic = false;
            var inputs = new StringBuilder().append(source.getMaxDamage()).append('/').append(source.getDamageValue())
                    .append(armorSample(ATTRIBUTES.query(source)));
            for (var value : sources.sourceTraits(source)) {
                Object resolved = definition.invoke(value.nativeInstance());
                if (value.id().equals(Identifier.parse("silentgear:chipping")) || value.id().equals(Identifier.parse("silentgear:crushing"))) {
                    var list = (List<?>) effects.invoke(resolved);
                    if (list.size() != 1 || list.getFirst().getClass() != number || value.level() > (int) maxLevel.invoke(resolved)) {
                        throw new IllegalStateException("Unsupported SG dynamic armor effects/level");
                    }
                    validateNumber(list.getFirst(), true);
                    dynamic = true;
                }
                if (value.id().equals(Identifier.parse("silentgear:chipping")) || value.id().equals(Identifier.parse("silentgear:crushing"))) {
                    inputs.append(value.id()).append('@').append(value.level()).append(jsonSample(traitCodec.encodeStart(JsonOps.INSTANCE, resolved).getOrThrow()));
                }
            }
            if (lane.isPresent()) {
                var copy = restore(source, lane.get());
                // Validate phase companions and successor even on selected/unchanged ticks.
                Object resolved = phase(lane.get());
                oxidationEffect(resolved);
                inputs.append(jsonSample(traitCodec.encodeStart(JsonOps.INSTANCE, resolved).getOrThrow()));
                lanes.seed(copy).orElseThrow();
            }
            return new Profile(dynamic, inputs.toString());
        }

        private void validateNumber(Object effect, boolean worn) throws ReflectiveOperationException {
            ready(effect);
            var values = (Map<?, ?>) mods.get(effect);
            if (values.isEmpty() || values.size() > 3 || worn && !values.containsKey(armor)
                    || values.keySet().stream().anyMatch(key -> !List.of(armor, attack, speed).contains(key))) {
                throw new IllegalStateException("Unsupported SG armor property definition");
            }
            for (Object value : values.values()) {
                ready(value);
                if (!(boolean) originalValue.invoke(value) || (boolean) damageRatio.invoke(value) != worn
                        || !Float.isFinite((float) multiplier.invoke(value))) {
                    throw new IllegalStateException("Unsupported SG numeric companion math");
                }
            }
        }

        private Object phase(SilentGearState.Oxidation lane) throws ReflectiveOperationException {
            return ((Supplier<?>) reference.invoke(null, "silentgear:" + lane.phase())).get();
        }

        private Object oxidationEffect(Object resolved) throws ReflectiveOperationException {
            return oxidationEffect(resolved, true);
        }

        private Object oxidationEffect(Object resolved, boolean validateSuccessor) throws ReflectiveOperationException {
            Object selected = null;
            for (Object effect : (List<?>) effects.invoke(resolved)) {
                if (effect.getClass() == oxidation && selected == null) { selected = effect; }
                else if (effect.getClass() == number) { validateNumber(effect, false); }
                else { throw new IllegalStateException("Unknown SG oxidation companion effect"); }
            }
            if (selected == null) { throw new IllegalStateException("Missing SG oxidation effect"); }
            ready(selected);
            int limit = (int) maxLevel.invoke(resolved);
            var thresholds = (List<?>) times.get(selected);
            if (limit < 1 || limit > Byte.MAX_VALUE || thresholds.size() < limit || thresholds.size() > 100
                    || thresholds.stream().anyMatch(value -> !(value instanceof Integer time) || time < 1 || time == Integer.MAX_VALUE)) {
                throw new IllegalStateException("Unsupported SG oxidation thresholds/maximum");
            }
            var successor = (Optional<?>) next.get(selected);
            if (successor.isPresent() && !((Supplier<?>) successor.get()).get().equals(
                    ((Supplier<?>) reference.invoke(null, "silentgear:oxidized")).get())) {
                throw new IllegalStateException("Unknown SG oxidation successor");
            }
            if (validateSuccessor && successor.isPresent()) {
                Object following = oxidationEffect(((Supplier<?>) successor.get()).get(), false);
                if (((Optional<?>) next.get(following)).isPresent()) {
                    throw new IllegalStateException("Unsupported SG oxidation successor cycle");
                }
            }
            return selected;
        }

        @SuppressWarnings("unchecked")
        private ItemStack restore(ItemStack source, SilentGearState.Oxidation lane) throws ReflectiveOperationException {
            if (lanes.preserve(source, Optional.of(lane)).isEmpty()) {
                throw new IllegalStateException("Live oxidation lost computed source eligibility");
            }
            var copy = source.copy();
            var values = new ArrayList<Object>();
            for (var value : sources.sourceTraits(copy)) {
                if (!value.id().equals(Identifier.parse("silentgear:polished")) && !value.id().equals(Identifier.parse("silentgear:oxidized"))) {
                    values.add(value.nativeInstance());
                }
            }
            values.add(of.invoke(null, phase(lane), lane.level(), List.of()));
            var map = new LinkedHashMap<>((Map<Object, Object>) propertyMap.invoke(copy.get(propertyComponent)));
            map.put(traitKey, traitList.newInstance(values));
            copy.set(propertyComponent, properties.newInstance(map));
            copy.set(stage, (byte) lane.stage());
            copy.set(counter, lane.counter());
            copy.remove(flag);
            return copy;
        }

        private Optional<SilentGearState.Oxidation> advance(ItemStack source, Optional<SilentGearState.Oxidation> lane,
                                                           ServerLevel level, Entity entity) throws ReflectiveOperationException {
            if (lane.isEmpty()) { return lane; }
            var value = lane.get();
            var copy = restore(source, value);
            Object resolved = phase(value);
            Object effect = oxidationEffect(resolved);
            Object nativeInstance = of.invoke(null, resolved, value.level(), List.of());
            // Predict the exact allowed write set/delta before executing native code on the disposable carrier.
            var expected = copy.copy();
            int limit = (int) maxLevel.invoke(resolved);
            var successor = (Optional<?>) next.get(effect);
            boolean active = !(boolean) hasPart.invoke(null, copy, coating) && (value.stage() < limit || successor.isPresent());
            if (active) {
                int count = value.counter() + 1;
                int threshold = (int) ((List<?>) times.get(effect)).get(value.level() - 1);
                if (count >= threshold) {
                    count = 0;
                    expected.set(stage, (byte) Math.min(value.stage() + 1, limit));
                    expected.set(flag, net.minecraft.util.Unit.INSTANCE);
                }
                expected.set(counter, count);
            }
            tick.invoke(effect, context.newInstance(entity instanceof Player player ? player : null, nativeInstance, copy), level, entity, false);
            if (!ItemStack.matches(expected, copy)) { throw new IllegalStateException("Native SG oxidation escaped its scalar/write boundary"); }
            settle(copy, entity instanceof Player player ? player : null);
            var captured = lanes.seed(copy).orElseThrow(() -> new IllegalStateException("Native oxidation lost phase"));
            oxidationEffect(phase(captured));
            return Optional.of(captured);
        }

        private void settle(ItemStack copy, Player player) throws ReflectiveOperationException {
            if (copy.has(flag)) {
                recalculate.invoke(null, copy, player);
                copy.remove(flag);
            }
        }

        private ItemAttributeModifiers attributes(ItemStack source, ItemStack root, Optional<SilentGearState.Oxidation> lane,
                                                   boolean dynamic) throws ReflectiveOperationException {
            var original = armor(ATTRIBUTES.query(source));
            if (!dynamic) { return original; }
            var copy = lane.isPresent() ? restore(source, lane.get()) : source.copy();
            int maximum = copy.getMaxDamage();
            if (maximum < 1 || root.getMaxDamage() < 0 || root.getDamageValue() < 0 || root.getDamageValue() > root.getMaxDamage()) {
                throw new IllegalStateException("Invalid native/root damage proportion");
            }
            copy.set(DataComponents.DAMAGE, root.getMaxDamage() == 0 ? 0 : (int) ((long) root.getDamageValue() * maximum / root.getMaxDamage()));
            recalculate.invoke(null, copy, null);
            var computed = armor(ATTRIBUTES.query(copy));
            SilentGearState.Projection.capture(computed); // Validate finite/bounded metadata before publication.
            return computed;
        }

        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static void ready(Object effect) {
            if (!(effect instanceof SilentGearLiveReadiness proof) || !proof.mismatchedleggings$sgLiveReady()) {
                throw new IllegalStateException("Native SG live callback surface changed");
            }
        }
    }
}
