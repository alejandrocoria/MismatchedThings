package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Frozen policy-aware source projections; live charge/config selection is read-only and precedes foreign events. */
public final class JustDireAttributes {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LeggingsContent.MOD_ID);
    private static final Supplier<DataComponentType<JustDireAttributeProjections>> PROJECTIONS = TYPES.register("just_dire_attribute_projections", () ->
            DataComponentType.<JustDireAttributeProjections>builder().persistent(JustDireAttributeProjections.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(JustDireAttributeProjections.CODEC)).build());
    private static final NeoForgeCompositionAttributes ATTRIBUTES = new NeoForgeCompositionAttributes();
    private static volatile Api api;
    private static MethodHandle readiness;
    private static boolean inspected;
    private static final Map<Player, ItemAttributeModifiers> WORN = new WeakHashMap<>();

    private JustDireAttributes() {}

    public static DataComponentType<JustDireAttributeProjections> projections() { return PROJECTIONS.get(); }

    public static void initialize(IEventBus bus) {
        TYPES.register(bus);
        if (!ModList.get().isLoaded("justdirethings")) { return; }
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (!event.getEntity().level().isClientSide()) { refreshWorn(event.getEntity()); }
        });
        bus.addListener((RegisterCapabilitiesEvent event) -> {
            try {
                var powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredTool");
                var phase = (AttributeModifier) Class.forName(
                        "com.direwolf20.justdirethings.common.events.PlayerEvents").getField("phase").get(null);
                var phaseAttribute = BuiltInRegistries.ATTRIBUTE.get(JustDireState.id("phase")).orElseThrow();
                if (!phase.id().equals(JustDireState.id("justdirephase")) || phase.amount() != 1
                        || phase.operation() != AttributeModifier.Operation.ADD_VALUE) {
                    throw new IllegalStateException("Unsupported native Phase modifier");
                }
                api = new Api(MethodHandles.publicLookup().findVirtual(powered, "getBlockBreakFECost", MethodType.methodType(int.class))
                        .asType(MethodType.methodType(int.class, Item.class)),
                        MethodHandles.publicLookup().findVirtual(powered, "getMaxEnergy", MethodType.methodType(int.class))
                                .asType(MethodType.methodType(int.class, Item.class)),
                        new ItemAttributeModifiers.Entry(phaseAttribute, phase, EquipmentSlotGroup.LEGS));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LeggingsContent.LOG.warn("Could not bind Just Dire Things leggings live attributes", failure);
            }
        });
    }

    static void prepare(ItemStack previous, ItemStack output, MixedLeggingsData data, AttributeRuleSet rules) {
        output.remove(projections());
        if (!participating(data)) { return; }
        Api binding = api;
        if (binding == null || !ready()) {
            safeBaseline(previous, output);
            return;
        }
        List<ItemAttributeModifiers> first;
        List<ItemAttributeModifiers> second;
        try {
            first = states(data.left().source(), binding);
            second = states(data.right().source(), binding);
        } catch (Throwable failure) {
            disable(binding, failure);
            safeBaseline(previous, output);
            return;
        }
        var merged = new ArrayList<ItemAttributeModifiers>(9);
        // User policy rejection must propagate, not be mistaken for an optional API failure.
        for (var a : first) {
            for (var b : second) {
                var calculated = AttributeMerger.merge(a, b, rules::policyFor).primary();
                merged.add(new ItemAttributeModifiers(calculated.modifiers().stream().filter(JustDireAttributeProjections::managed).toList()));
            }
        }
        var table = new JustDireAttributeProjections(merged);
        var current = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var baseline = replace(current, table.states().getFirst());
        if (previous != null && previous.has(projections())) {
            var old = previous.get(projections()).states().getFirst();
            var previousAttributes = previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            baseline = applyOverrides(baseline, old, previousAttributes);
        }
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, baseline);
        output.set(projections(), table);
    }

    private static boolean participating(MixedLeggingsData data) {
        return JustDireAbilities.nativeItem(data.left().source()) || JustDireAbilities.nativeItem(data.right().source());
    }

    private static List<ItemAttributeModifiers> states(ItemStack source, Api binding) throws Throwable {
        var result = new ArrayList<ItemAttributeModifiers>(3);
        for (int state = 0; state < 3; state++) {
            var view = source.copy();
            if (JustDireAbilities.nativeItem(view)) {
                view.set(JustDireState.component("tool_enabled"), true);
                view.set(JustDireState.component("phase_upgrade_installed"), true);
                view.set(JustDireState.component("phase_toggle"), state == 2);
                if (JustDireAbilities.powered(view)) {
                    int capacity = (int) binding.capacity.invokeExact(view.getItem());
                    if (capacity <= 0) { throw new IllegalStateException("Invalid powered leggings capacity"); }
                    view.set(JustDireState.component("forge_energy"), state == 0 ? 0 : capacity);
                }
            }
            var queried = ATTRIBUTES.query(view);
            if (JustDireAbilities.nativeItem(view)) {
                var entries = new ArrayList<>(queried.modifiers().stream().filter(entry ->
                        !JustDireAttributeProjections.key(entry).equals(JustDireAttributeProjections.key(binding.phase))).toList());
                // Freeze the native contribution independently of today's config. Live eligibility still gates selection.
                if (state == 2 && BuiltInRegistries.ITEM.getKey(view.getItem()).getPath().equals("eclipsealloy_leggings")) {
                    entries.add(binding.phase);
                }
                queried = new ItemAttributeModifiers(entries);
            }
            result.add(queried);
        }
        return result;
    }

    private static int current(ItemStack source, ItemStack root, Api binding) throws Throwable {
        if (!JustDireAbilities.nativeItem(source)) { return 1; }
        if (JustDireAbilities.powered(source)) {
            var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
            int threshold = (int) binding.threshold.invokeExact(source.getItem());
            if (threshold < 0) { throw new IllegalStateException("Invalid native armor threshold"); }
            if (energy == null || energy.getAmountAsLong() < threshold) { return 0; }
        }
        return JustDireAbilities.usable(JustDireAbilities.view(root, "phase"), "phase")
                && BuiltInRegistries.ITEM.getKey(source.getItem()).getPath().equals("eclipsealloy_leggings") ? 2 : 1;
    }

    public static ItemAttributeModifiers project(ItemStack root, ItemAttributeModifiers defaults) {
        if (!MixedLeggingsStructure.isMixedItem(root)) { return defaults; }
        var table = root.get(projections());
        if (table == null) { return defaults; }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) { return defaults; }
        Api binding = api;
        int state = 0; // Saved depleted baseline remains safe if the binding/hook is lost.
        if (binding != null && ready()) {
            try { state = current(data.left().source(), root, binding) * 3 + current(data.right().source(), root, binding); }
            catch (Throwable failure) { disable(binding, failure); }
        }
        var baseline = table.states().getFirst();
        var live = table.states().get(state);
        var result = new ArrayList<>(defaults.modifiers());
        var sg = root.get(SilentGearCompatibility.state());
        for (var key : keys(List.of(baseline, live))) {
            if (!entries(defaults, key).equals(entries(baseline, key))
                    || sg != null && sg.armorOverridden() && key.attribute().equals(SilentGearState.ARMOR)
                    && key.modifier().equals(SilentGearState.MODIFIER)) { continue; }
            result.removeIf(entry -> JustDireAttributeProjections.key(entry).equals(key));
            result.addAll(entries(live, key));
        }
        return new ItemAttributeModifiers(result);
    }

    /** SG's already-settled live lane replaces only shared armor across the frozen native JDT states. */
    public static JustDireAttributeProjections withSourceArmor(ItemStack root, MixedLeggingsData data, AttributePolicy policy,
                                                               ItemAttributeModifiers left, ItemAttributeModifiers right) throws ReflectiveOperationException {
        var old = root.get(projections());
        if (old == null || !participating(data)) { return null; }
        Api binding = api;
        if (binding == null || !ready()) { return old; }
        try {
            var a = JustDireAbilities.nativeItem(data.left().source()) ? states(data.left().source(), binding) : List.of(left, left, left);
            var b = JustDireAbilities.nativeItem(data.right().source()) ? states(data.right().source(), binding) : List.of(right, right, right);
            var combined = new ArrayList<ItemAttributeModifiers>(9);
            for (int i = 0; i < 9; i++) {
                var armor = AttributeMerger.merge(a.get(i / 3), b.get(i % 3), id -> policy).primary();
                var entries = new ArrayList<>(old.states().get(i).modifiers().stream().filter(entry -> !SilentGearState.managed(entry)).toList());
                entries.addAll(armor.modifiers().stream().filter(SilentGearState::managed).toList());
                combined.add(new ItemAttributeModifiers(entries));
            }
            return new JustDireAttributeProjections(combined);
        } catch (Throwable failure) {
            disable(binding, failure);
            throw new ReflectiveOperationException("Cannot rebuild shared JDT/SG armor", failure);
        }
    }

    private static void safeBaseline(ItemStack previous, ItemStack output) {
        var current = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (previous != null && previous.has(projections())) {
            var baseline = previous.get(projections()).states().getFirst();
            output.set(DataComponents.ATTRIBUTE_MODIFIERS, applyOverrides(replace(current, baseline), baseline,
                    previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)));
        } else {
            // No reliable native snapshot: strip only captured Phase, never another mod's armor.
            output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(current.modifiers().stream()
                    .filter(entry -> !(JustDireAttributeProjections.managed(entry)
                            && JustDireAttributeProjections.key(entry).attribute().equals(JustDireState.id("phase")))).toList()));
        }
    }

    private static ItemAttributeModifiers replace(ItemAttributeModifiers current, ItemAttributeModifiers replacement) {
        var result = new ArrayList<>(current.modifiers().stream().filter(entry -> !JustDireAttributeProjections.managed(entry)).toList());
        result.addAll(replacement.modifiers());
        return new ItemAttributeModifiers(result);
    }

    private static void refreshWorn(Player player) {
        var root = player.getItemBySlot(EquipmentSlot.LEGS);
        var previous = WORN.getOrDefault(player, ItemAttributeModifiers.EMPTY);
        boolean managed = !root.isBroken() && root.has(projections()) && MixedLeggingsStructure.validData(root).isPresent();
        if (!managed && previous.modifiers().isEmpty()) { return; }
        var next = !root.isBroken() && (!MixedLeggingsStructure.isMixedItem(root) || MixedLeggingsStructure.validData(root).isPresent())
                ? new ItemAttributeModifiers(root.getAttributeModifiers().modifiers().stream().filter(JustDireAttributeProjections::managed).toList())
                : ItemAttributeModifiers.EMPTY;
        // Configuration changes need not change the ItemStack. Vanilla's equipment comparison cannot observe those.
        for (var key : keys(List.of(previous, next))) {
            var oldEntries = entries(previous, key);
            var newEntries = entries(next, key);
            var entry = !newEntries.isEmpty() ? newEntries.getFirst() : oldEntries.getFirst();
            var instance = player.getAttribute(entry.attribute());
            if (instance == null) { continue; }
            var actual = instance.getModifier(key.modifier());
            var oldModifier = oldEntries.isEmpty() ? null : oldEntries.getFirst().modifier();
            var newModifier = newEntries.isEmpty() ? null : newEntries.getFirst().modifier();
            if (!Objects.equals(actual, oldModifier) && !Objects.equals(actual, newModifier)) { continue; }
            if (!Objects.equals(actual, newModifier)) {
                instance.removeModifier(key.modifier());
                if (newModifier != null) { instance.addTransientModifier(newModifier); }
            }
        }
        if (!managed) { WORN.remove(player); }
        else { WORN.put(player, next); }
    }

    private static ItemAttributeModifiers applyOverrides(ItemAttributeModifiers target, ItemAttributeModifiers baseline, ItemAttributeModifiers current) {
        var result = new ArrayList<>(target.modifiers());
        for (var key : keys(List.of(baseline, current))) {
            if (entries(baseline, key).equals(entries(current, key))) { continue; }
            result.removeIf(entry -> JustDireAttributeProjections.key(entry).equals(key));
            result.addAll(entries(current, key));
        }
        return new ItemAttributeModifiers(result);
    }

    private static LinkedHashSet<JustDireAttributeProjections.Key> keys(List<ItemAttributeModifiers> states) {
        var result = new LinkedHashSet<JustDireAttributeProjections.Key>();
        states.forEach(state -> state.modifiers().stream().filter(JustDireAttributeProjections::managed)
                .forEach(entry -> result.add(JustDireAttributeProjections.key(entry))));
        return result;
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers value, JustDireAttributeProjections.Key key) {
        return value.modifiers().stream().filter(entry -> JustDireAttributeProjections.key(entry).equals(key)).toList();
    }

    private static synchronized boolean ready() {
        if (!inspected) {
            inspected = true;
            try {
                readiness = MethodHandles.privateLookupIn(CommonHooks.class, MethodHandles.lookup()).findStatic(CommonHooks.class,
                        "mismatchedleggings$justDireAttributesReady", MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException unavailable) { return false; }
        }
        try { return readiness != null && (boolean) readiness.invokeExact(); }
        catch (Throwable failure) { disable(api, failure); return false; }
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        if (api == binding && binding != null) {
            api = null;
            LeggingsContent.LOG.warn("Just Dire Things leggings live attribute query failed; projection disabled", failure);
        }
    }

    private record Api(MethodHandle threshold, MethodHandle capacity, ItemAttributeModifiers.Entry phase) {}
}
