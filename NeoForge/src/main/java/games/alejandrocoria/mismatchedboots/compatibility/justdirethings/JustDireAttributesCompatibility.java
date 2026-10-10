package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.composition.AttributeComposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/** Frozen native inputs at lifecycle boundaries; live, read-only pre-event selection. */
public final class JustDireAttributesCompatibility {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final Supplier<DataComponentType<JustDireAttributeProjections>> PROJECTIONS = TYPES.register(
            "just_dire_attribute_projections", () -> DataComponentType.<JustDireAttributeProjections>builder()
                    .persistent(JustDireAttributeProjections.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(JustDireAttributeProjections.CODEC)).build());
    private static volatile Api api;
    private static MethodHandle readiness;
    private static boolean inspected;

    private JustDireAttributesCompatibility() {}

    public static DataComponentType<JustDireAttributeProjections> projections() { return PROJECTIONS.get(); }

    public static void initialize(IEventBus bus) {
        // Register even when JDT is absent so saved depleted baselines remain readable.
        TYPES.register(bus);
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireAttributesCompatibility::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var lookup = MethodHandles.publicLookup();
            Class<?> powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredTool");
            Class<?> toggleable = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            Class<?> ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            Item celestigem = item("celestigem_boots"), eclipsealloy = item("eclipsealloy_boots");
            if (!powered.isInstance(celestigem) || !powered.isInstance(eclipsealloy)
                    || !toggleable.isInstance(celestigem) || !toggleable.isInstance(eclipsealloy)) {
                throw new IllegalStateException("Unsupported native powered boot API");
            }
            DataComponentType<Integer> energy = component("forge_energy");
            DataComponentType<Boolean> enabled = component("tool_enabled"), toggle = component("stepheight_toggle");
            if (energy == null || enabled == null || toggle == null) { throw new IllegalStateException("Missing native attribute component API"); }
            api = new Api(celestigem, eclipsealloy, energy, enabled,
                    toggle, ability.getField("STEPHEIGHT").get(null),
                    lookup.findVirtual(powered, "getBlockBreakFECost", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Item.class)),
                    lookup.findVirtual(powered, "getMaxEnergy", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Item.class)),
                    lookup.findVirtual(toggleable, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, ItemStack.class, Object.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things attribute API; live attributes disabled", failure);
        }
    }

    public static void prepare(ItemStack previous, ItemStack output, AttributeRuleSet rules) {
        output.remove(projections()); // Never carry a stale tier/rule table through generic projection.
        Api binding = api;
        if (binding == null || !ready() || !eligible(output)) {
            if (binding == null || !ready() || !JustDireUpgradesCompatibility.available()) { retainSafeBaseline(previous, output); }
            var baseline = new ArrayList<>(output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers());
            preserveOverrides(previous, baseline);
            output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(baseline)));
            return;
        }
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        var first = new ArrayList<ItemAttributeModifiers>(3);
        var second = new ArrayList<ItemAttributeModifiers>(3);
        try {
            ItemStack left = data.left().source(), right = data.right().source();
            for (int state = 0; state < 3; state++) {
                first.add(Services.PLATFORM.getCompositionAttributeModifiers(view(left, output, state, binding)));
                second.add(Services.PLATFORM.getCompositionAttributeModifiers(view(right, output, state, binding)));
            }
        } catch (Throwable failure) {
            disable(binding, failure);
            retainSafeBaseline(previous, output);
            return;
        }
        if (api != binding) { return; }
        var states = new ArrayList<ItemAttributeModifiers>(9);
        // Policy/data rejection is not an optional-API failure; publish only after all states succeed.
        for (var a : first) { for (var b : second) {
            states.add(new ItemAttributeModifiers(AttributeComposer.composePrimary(a, b, rules).modifiers()
                    .stream().filter(JustDireAttributeProjections::managed).toList()));
        } }
        var table = new JustDireAttributeProjections(states);
        var baseline = new ArrayList<>(output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream().filter(entry -> !JustDireAttributeProjections.managed(entry)).toList());
        baseline.addAll(table.states().getFirst().modifiers());
        preserveOverrides(previous, baseline);
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(baseline)));
        output.set(projections(), table);
    }

    private static void retainSafeBaseline(ItemStack previous, ItemStack output) {
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        boolean nativeBoot = java.util.stream.Stream.of(data.left().source(), data.right().source()).anyMatch(source -> {
            String id = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
            return List.of("justdirethings:ferricore_boots", "justdirethings:blazegold_boots",
                    "justdirethings:celestigem_boots", "justdirethings:eclipsealloy_boots").contains(id);
        });
        if (!nativeBoot) { return; } // Ordinary replacement sources still use ordinary recomposition.
        if (previous == null || !previous.has(projections())) {
            // A fresh generic snapshot must not leave Step active when root routing is unavailable.
            var defaults = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(defaults.modifiers().stream()
                    .filter(entry -> !(entry.modifier().id().equals(Identifier.fromNamespaceAndPath("justdirethings", "justdirestepassist"))
                            && BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()).equals(Identifier.withDefaultNamespace("step_height"))))
                    .toList()));
            return;
        }
        // No optional native calls are required to retain a saved safe baseline after losing routing/API.
        var fallback = new ArrayList<>(output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream().filter(entry -> !JustDireAttributeProjections.managed(entry)).toList());
        fallback.addAll(previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream().filter(JustDireAttributeProjections::managed).toList());
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(fallback)));
    }

    private static void preserveOverrides(ItemStack previous, List<ItemAttributeModifiers.Entry> baseline) {
        if (previous != null && previous.has(projections())) {
            var old = previous.get(projections());
            var current = previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            var managedKeys = keys(old.states());
            current.modifiers().stream().filter(JustDireAttributeProjections::managed)
                    .forEach(entry -> managedKeys.add(JustDireAttributeProjections.key(entry)));
            for (var key : managedKeys) {
                if (entries(current, key).equals(entries(old.states().getFirst(), key))) { continue; }
                baseline.removeIf(entry -> JustDireAttributeProjections.key(entry).equals(key));
                baseline.addAll(entries(current, key)); // Explicit add/edit/removal wins even after rebuilding.
            }
        }
    }

    public static ItemAttributeModifiers project(ItemStack root, ItemAttributeModifiers defaults) {
        if (!(root.getItem() instanceof MixedBootsItem)) { return defaults; }
        Api binding = api;
        var table = root.get(projections());
        if (table == null || binding == null || !ready() || !eligible(root)) { return defaults; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        int state;
        try { state = current(data.left().source(), root, binding) * 3 + current(data.right().source(), root, binding); }
        catch (Throwable failure) { disable(binding, failure); return defaults; }
        if (api != binding) { return defaults; }
        var baseline = table.states().getFirst();
        var live = table.states().get(state);
        var result = new ArrayList<>(defaults.modifiers());
        for (var key : keys(List.of(baseline, live))) {
            if (!entries(defaults, key).equals(entries(baseline, key))) { continue; }
            result.removeIf(entry -> JustDireAttributeProjections.key(entry).equals(key));
            result.addAll(entries(live, key));
        }
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    private static boolean eligible(ItemStack root) {
        if (root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return false; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return false; }
        return JustDireUpgradesCompatibility.nativeBoot(data.left().source())
                || JustDireUpgradesCompatibility.nativeBoot(data.right().source());
    }

    private static ItemStack view(ItemStack source, ItemStack root, int state, Api binding) throws Throwable {
        if (!JustDireUpgradesCompatibility.nativeBoot(source)) { return source; }
        JustDireUpgradesCompatibility.overlayInstallation(source, root);
        source.set(binding.enabled, true);
        source.set(binding.toggle, state == 2);
        if (binding.powered(source)) {
            int capacity = (int) binding.capacity.invokeExact(source.getItem());
            if (capacity <= 0) { throw new IllegalStateException("Invalid native powered capacity"); }
            source.set(binding.energy, state == 0 ? 0 : capacity);
        }
        return source;
    }

    private static int current(ItemStack source, ItemStack root, Api binding) throws Throwable {
        if (!JustDireUpgradesCompatibility.nativeBoot(source)) { return 1; }
        if (!JustDireUpgradesCompatibility.overlayInstallation(source, root)) { return 0; }
        if (binding.powered(source)) {
            if (net.neoforged.neoforge.transfer.access.ItemAccess.forStack(root)
                    .getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.ITEM) == null) { return 0; }
            int charge = root.getOrDefault(binding.energy, 0);
            int threshold = (int) binding.threshold.invokeExact(source.getItem());
            if (threshold < 0) { throw new IllegalStateException("Invalid native availability threshold"); }
            if (charge < threshold) { return 0; }
            int capacity = (int) binding.capacity.invokeExact(source.getItem());
            if (capacity <= 0) { throw new IllegalStateException("Invalid native powered capacity"); }
            source.set(binding.energy, Math.min(charge, capacity));
        }
        source.set(binding.enabled, root.getOrDefault(binding.enabled, true));
        source.set(binding.toggle, root.getOrDefault(binding.toggle, true));
        return (boolean) binding.usable.invokeExact(source.getItem(), source, binding.step) ? 2 : 1;
    }

    private static synchronized boolean ready() {
        if (!inspected) {
            inspected = true;
            try { readiness = MethodHandles.privateLookupIn(CommonHooks.class, MethodHandles.lookup()).findStatic(CommonHooks.class,
                    "mismatchedboots$justDireAttributesReady", MethodType.methodType(boolean.class)); }
            catch (NoSuchMethodException | IllegalAccessException missingHook) { return false; }
        }
        if (readiness == null) { return false; }
        try { return (boolean) readiness.invokeExact(); }
        catch (Throwable failure) { disable(api, failure); return false; }
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        synchronized (JustDireAttributesCompatibility.class) {
            if (binding != null && api == binding) {
                api = null;
                Constants.LOG.warn("Just Dire Things attribute query failed; live attributes disabled", failure);
            }
        }
    }

    private static LinkedHashSet<JustDireAttributeProjections.Key> keys(List<ItemAttributeModifiers> states) {
        var keys = new LinkedHashSet<JustDireAttributeProjections.Key>();
        states.forEach(state -> state.modifiers().forEach(entry -> keys.add(JustDireAttributeProjections.key(entry))));
        return keys;
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers modifiers, JustDireAttributeProjections.Key key) {
        return modifiers.modifiers().stream().filter(entry -> JustDireAttributeProjections.key(entry).equals(key)).toList();
    }

    private static Item item(String path) { return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", path)); }
    @SuppressWarnings("unchecked") private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    private record Api(Item celestigem, Item eclipsealloy, DataComponentType<Integer> energy,
                       DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle, Object step,
                       MethodHandle threshold, MethodHandle capacity, MethodHandle usable) {
        boolean powered(ItemStack stack) { return stack.is(celestigem) || stack.is(eclipsealloy); }
    }
}
