package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributeProjections;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.AttributeComposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** One copy/validate/publish transaction for SG clocks, armor and shared JDT projections. */
public final class SilentGearLiveState {
    private static final SilentGearState.Key ARMOR = new SilentGearState.Key(Identifier.withDefaultNamespace("armor"),
                                                                             Identifier.withDefaultNamespace("armor.boots"), EquipmentSlotGroup.FEET);
    private static Native api;
    private static boolean disabled;

    private SilentGearLiveState() {
    }

    static void prepare(ItemStack root) {
        update(root, null, null, null, true);
    }

    static Optional<SilentGearState.Oxidation> seed(ItemStack source, SilentGearCompatibility.Api binding) {
        var lane = binding.oxidation(source);
        if (lane.isEmpty() || disabled) {
            return lane;
        }
        try {
            Native nativeApi = nativeApi();
            if (!source.has(nativeApi.flag)) {
                return lane;
            }
            ItemStack copy = source.copy();
            nativeApi.recalculate.invoke(null, copy, null);
            return Optional.of(binding.oxidation(copy).orElseThrow(() -> new IllegalStateException("Pending native oxidation lost phase")));
        } catch (Throwable failure) {
            disable(failure);
            return lane; // Optional C binding failure must not disable A's source reader or B's effects.
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        update(root, level, entity, slot, false);
    }

    private static void update(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot, boolean initial) {
        var binding = SilentGearCompatibility.binding();
        var old = root.get(SilentGearCompatibility.state());
        var data = ModContent.getMixedBootsData(root).orElse(null);
        var equipment = root.get(DataComponents.EQUIPPABLE);
        if (disabled || binding == null || old == null || data == null || root.getCount() != 1
                || !(root.getItem() instanceof MixedBootsItem) || equipment == null || equipment.slot() != EquipmentSlot.FEET) {
            return;
        }
        try {
            Native nativeApi = nativeApi();
            ItemStack left = data.left().source(), right = data.right().source();
            String firstDefinition = nativeApi.profile(left, binding), secondDefinition = nativeApi.profile(right, binding);
            boolean dynamic = !firstDefinition.isEmpty() || !secondDefinition.isEmpty();
            Optional<SilentGearState.Sample> sample = dynamic
                                                      ? Optional.of(new SilentGearState.Sample(root.getDamageValue(),
                                                                                               root.getMaxDamage(),
                                                                                               firstDefinition + "|" + secondDefinition))
                                                      : Optional.empty();
            boolean refresh = initial || !old.sample().equals(sample);
            Optional<SilentGearState.Oxidation> a = old.left(), b = old.right();
            if (!initial && slot == null && entity != null && entity.tickCount % 20 == 0) {
                a = nativeApi.advance(left, a, level, entity, binding);
                b = nativeApi.advance(right, b, level, entity, binding);
                refresh |= phaseChanged(old.left(), a) || phaseChanged(old.right(), b);
            }
            var keys = new LinkedHashSet<SilentGearState.Key>();
            old.projection().modifiers().forEach(entry -> keys.add(SilentGearState.Key.of(entry)));
            keys.addAll(old.overrides());
            if (dynamic) {
                keys.add(ARMOR);
            }
            var current = root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            var overrides = new LinkedHashSet<>(old.overrides());
            for (var key : keys) {
                if (!entries(current, key).equals(entries(old.projection(), key))) {
                    overrides.add(key);
                }
            }
            ItemAttributeModifiers projection = old.projection();
            JustDireAttributeProjections table = root.get(JustDireAttributesCompatibility.projections());
            if (refresh && dynamic) {
                Player player = entity instanceof Player value ? value : null;
                var first = nativeApi.attributes(left, root, a, player, binding);
                var second = nativeApi.attributes(right, root, b, player, binding);
                var rules = rules(old);
                var calculated = AttributeComposer.composePrimary(first, second, rules);
                if (table != null) {
                    var rebuilt = JustDireAttributesCompatibility.withSourceAttributes(root, rules, first, second);
                    var states = new ArrayList<ItemAttributeModifiers>(9);
                    for (int i = 0; i < 9; i++) {
                        states.add(replace(table.states().get(i), ARMOR, entries(rebuilt.states().get(i), ARMOR)));
                    }
                    table = new JustDireAttributeProjections(states);
                    calculated = replace(calculated, ARMOR, entries(table.states().getFirst(), ARMOR));
                }
                projection = replace(projection, ARMOR, entries(calculated, ARMOR));
            }
            var attributes = current;
            if (refresh && dynamic && !overrides.contains(ARMOR)) {
                attributes = replace(current, ARMOR, entries(projection, ARMOR));
            }
            var next = new SilentGearState(a, b, old.rules(), projection, List.copyOf(overrides), sample);
            // Nothing touches the real root until every source and all nine shared states validate.
            if (initial) {
                root.remove(nativeApi.flag);
            } // Native pending work was settled on seed copies, never by the root.
            if (!attributes.equals(current)) {
                root.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
            }
            if (table != null && !table.equals(root.get(JustDireAttributesCompatibility.projections()))) {
                root.set(JustDireAttributesCompatibility.projections(), table);
            }
            if (!next.equals(old)) {
                root.set(SilentGearCompatibility.state(), next);
            }
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    private static synchronized void disable(Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
            throw fatal;
        }
        if (!disabled) {
            disabled = true;
            Constants.LOG.warn("Silent Gear mixed state disabled: native projection/oxidation contract unavailable", failure);
        }
    }

    private static boolean phaseChanged(Optional<SilentGearState.Oxidation> before, Optional<SilentGearState.Oxidation> after) {
        return before.isPresent() != after.isPresent() || before.isPresent()
                && (!before.get().phase().equals(after.get().phase()) || before.get().level() != after.get().level()
                || before.get().stage() != after.get().stage());
    }

    private static AttributeRuleSet rules(SilentGearState state) {
        var resolved = new LinkedHashMap<Identifier, AttributePolicy>();
        state.rules().forEach(rule -> resolved.put(rule.attribute(), rule.policy()));
        return new AttributeRuleSet(AttributePolicy.SEPARATE, resolved);
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers attributes, SilentGearState.Key key) {
        return attributes.modifiers().stream().filter(entry -> SilentGearState.Key.of(entry).equals(key)).toList();
    }

    private static ItemAttributeModifiers replace(ItemAttributeModifiers attributes,
                                                  SilentGearState.Key key,
                                                  List<ItemAttributeModifiers.Entry> values) {
        var result = new ArrayList<>(attributes.modifiers().stream().filter(entry -> !SilentGearState.Key.of(entry).equals(key)).toList());
        result.addAll(values);
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    private static synchronized Native nativeApi() throws ReflectiveOperationException {
        if (api == null) {
            api = new Native();
        }
        return api;
    }

    private static final class Native {
        private final Class<?> trait = type("gear.trait.Trait"), instance = type("api.traits.TraitInstance"), resource = type("api.util.DataResource");
        private final Class<?> oxidation = type("gear.trait.effect.OxidationTraitEffect"), number = type(
                "gear.trait.effect.NumberPropertyModifierTraitEffect");
        private final Method recalculate = type("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class);
        private final Method traits = type("core.component.GearPropertiesData").getMethod("properties");
        private final Method effects = trait.getMethod("getEffects"), definition = instance.getMethod("getTrait");
        private final Method of = instance.getMethod("of", trait, int.class, Collection.class);
        private final Method reference = resource.getMethod("trait", String.class);
        private final Constructor<?> context = type("api.traits.TraitActionContext").getConstructor(Player.class, instance, ItemStack.class);
        private final Constructor<?> properties = type("core.component.GearPropertiesData").getConstructor(Map.class);
        private final Constructor<?> traitList = type("api.property.TraitListPropertyValue").getConstructor(List.class);
        private final Method tick = oxidation.getMethod("inventoryTick", context.getDeclaringClass(), Level.class, Entity.class, boolean.class);
        private final Field mods = field(number, "mods");
        private final Field next = field(oxidation, "nextOxidationTrait");
        private final Method multiplier = type("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplier");
        private final Method damageRatio = type("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplyDamageRatio");
        private final Method originalValue = type("gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod").getMethod("multiplyOriginalValue");
        private final Object traitKey = ((Supplier<?>) type("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        private final Object armor = ((Supplier<?>) type("setup.gear.GearProperties").getField("ARMOR").get(null)).get();
        private final DataComponentType<Object> propertyComponent = component("GEAR_PROPERTIES"), flag = component("RECALCULATE_FLAG");

        private Native() throws ReflectiveOperationException {
        }

        private String profile(ItemStack source, SilentGearCompatibility.Api binding) throws ReflectiveOperationException {
            var found = new ArrayList<String>();
            for (var value : binding.traits(source)) {
                if (!List.of("silentgear:chipping", "silentgear:crushing").contains(value.id().toString())) {
                    continue;
                }
                var list = (List<?>) effects.invoke(definition.invoke(value.nativeInstance()));
                if (list.size() != 1 || !number.isInstance(list.getFirst())) {
                    throw new IllegalStateException("Unsupported SG dynamic trait effect");
                }
                Object modifier = ((Map<?, ?>) mods.get(list.getFirst())).get(armor);
                if (modifier == null || !(boolean) damageRatio.invoke(modifier)
                        || !(boolean) originalValue.invoke(modifier) || !Float.isFinite((float) multiplier.invoke(modifier))) {
                    throw new IllegalStateException("Unsupported SG damage-dependent armor definition");
                }
                found.add(value.id() + "@" + value.level() + "=" + multiplier.invoke(modifier));
            }
            found.sort(String::compareTo);
            return String.join(";", found);
        }

        @SuppressWarnings("unchecked")
        private ItemStack restore(ItemStack source,
                                  SilentGearState.Oxidation lane,
                                  SilentGearCompatibility.Api binding) throws ReflectiveOperationException {
            binding.validateLane(source, lane);
            ItemStack copy = source.copy();
            Object phase = ((Supplier<?>) reference.invoke(null, "silentgear:" + lane.phase())).get();
            var values = new ArrayList<Object>();
            for (var value : binding.traits(copy)) {
                if (!List.of("polished", "oxidized").contains(value.id().getPath())) {
                    values.add(value.nativeInstance());
                }
            }
            values.add(of.invoke(null, phase, lane.level(), List.of()));
            var map = new LinkedHashMap<>((Map<Object, Object>) traits.invoke(copy.get(propertyComponent)));
            map.put(traitKey, traitList.newInstance(values));
            copy.set(propertyComponent, properties.newInstance(map));
            copy.set(binding.stage, (byte) lane.stage());
            copy.set(binding.counter, lane.counter());
            copy.remove(flag);
            return copy;
        }

        private Optional<SilentGearState.Oxidation> advance(ItemStack source,
                                                            Optional<SilentGearState.Oxidation> lane,
                                                            ServerLevel level,
                                                            Entity entity,
                                                            SilentGearCompatibility.Api binding) throws ReflectiveOperationException {
            if (lane.isEmpty()) {
                return lane;
            }
            ItemStack copy = restore(source, lane.get(), binding), before = copy.copy();
            Object value = binding.traits(copy)
                                  .stream()
                                  .filter(item -> item.id().getPath().equals(lane.get().phase()))
                                  .findFirst()
                                  .orElseThrow()
                                  .nativeInstance();
            var candidates = ((List<?>) effects.invoke(definition.invoke(value))).stream().filter(oxidation::isInstance).toList();
            if (candidates.size() != 1 || candidates.getFirst().getClass() != oxidation) {
                throw new IllegalStateException("Unsupported SG oxidation effect");
            }
            var following = (Optional<?>) next.get(candidates.getFirst());
            if (following.isPresent() && !((Supplier<?>) following.get()).get()
                                                                         .equals(((Supplier<?>) reference.invoke(null,
                                                                                                                 "silentgear:oxidized")).get())) {
                throw new IllegalStateException("Unsupported SG oxidation successor");
            }
            tick.invoke(candidates.getFirst(),
                        context.newInstance(entity instanceof Player player ? player : null, value, copy),
                        level,
                        entity,
                        false);
            ItemStack protectedCopy = copy.copy();
            for (var component : List.of(binding.stage, binding.counter, flag)) {
                before.remove(component);
                protectedCopy.remove(component);
            }
            if (!ItemStack.matches(before, protectedCopy)) {
                throw new IllegalStateException("Native oxidation escaped its state boundary");
            }
            int stage = ((Number) copy.getOrDefault(binding.stage, (byte) 0)).intValue();
            int counter = (int) copy.getOrDefault(binding.counter, 0);
            if (stage < lane.get().stage() || stage > lane.get().stage() + 1
                    || counter != lane.get().counter() && counter != lane.get().counter() + 1 && counter != 0) {
                throw new IllegalStateException("Unexpected native oxidation delta");
            }
            if (copy.has(flag)) {
                // Rebuild from original native construction, INCLUDING its observed Polished reset.
                // Keeping the live phase through this recalculation would silently fix SG's own behavior.
                recalculate.invoke(null, copy, entity instanceof Player player ? player : null);
                copy.remove(flag);
            }
            return Optional.of(binding.oxidation(copy).orElseThrow(() -> new IllegalStateException("Native oxidation lost its phase")));
        }

        private ItemAttributeModifiers attributes(ItemStack source, ItemStack root, Optional<SilentGearState.Oxidation> lane,
                                                  Player player, SilentGearCompatibility.Api binding) throws ReflectiveOperationException {
            if (profile(source, binding).isEmpty()) {
                return Services.PLATFORM.getCompositionAttributeModifiers(source);
            }
            ItemStack copy = lane.isPresent() ? restore(source, lane.get(), binding) : source.copy();
            int maximum = copy.getMaxDamage();
            if (maximum < 1 || root.getMaxDamage() < 1 || root.getDamageValue() < 0 || root.getDamageValue() > root.getMaxDamage()) {
                throw new IllegalStateException("Invalid native/root damage proportion");
            }
            copy.set(DataComponents.DAMAGE, (int) ((long) root.getDamageValue() * maximum / root.getMaxDamage()));
            recalculate.invoke(null, copy, player);
            var original = Services.PLATFORM.getCompositionAttributeModifiers(source);
            var computed = Services.PLATFORM.getCompositionAttributeModifiers(copy);
            if (computed.modifiers().stream().anyMatch(entry -> !Double.isFinite(entry.modifier().amount()))) {
                throw new IllegalStateException("Nonfinite native armor projection");
            }
            // Only audited damage-dependent armor leaves the native working stack.
            return replace(original, ARMOR, entries(computed, ARMOR));
        }

        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName("net.silentchaos512.gear." + name);
        }

        @SuppressWarnings("unchecked")
        private static DataComponentType<Object> component(String name) throws ReflectiveOperationException {
            return (DataComponentType<Object>) ((Supplier<?>) type("setup.SgDataComponents").getField(name).get(null)).get();
        }
    }
}
