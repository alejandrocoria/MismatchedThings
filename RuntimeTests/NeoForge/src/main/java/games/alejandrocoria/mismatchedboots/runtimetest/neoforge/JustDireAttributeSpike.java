package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Disposable local prototype. No production factory, item or mixin references this class. */
public final class JustDireAttributeSpike {
    // Three states per source: unavailable, armor only, armor + installed Step Assist.
    // State zero is the persisted safe baseline; no second mutable FE balance.
    static final Codec<List<ItemAttributeModifiers>> CODEC = ItemAttributeModifiers.CODEC.listOf()
            .validate(states -> states.size() == 9
                    && states.stream().allMatch(state -> state.modifiers().size() <= 32
                    && state.modifiers().stream().allMatch(JustDireAttributeSpike::managed))
                    ? DataResult.success(states) : DataResult.error(() -> "Invalid bounded attribute projections"));
    private static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "mismatchedboots_runtime_test");
    private static final java.util.function.Supplier<DataComponentType<List<ItemAttributeModifiers>>> PROJECTIONS =
            TYPES.register("just_dire_attribute_projections", () -> DataComponentType.<List<ItemAttributeModifiers>>builder()
                    .persistent(CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CODEC)).build());
    public static boolean hookSeen;
    public static boolean earlierOverride;
    private static Method compose;
    private static Method primary;
    private static Method threshold;
    private static Method canUse;
    private static Object step;

    private JustDireAttributeSpike() {}

    static void register(IEventBus bus) { TYPES.register(bus); }

    static void bind() throws ReflectiveOperationException {
        Class<?> composer = Class.forName("games.alejandrocoria.mismatchedboots.composition.AttributeComposer");
        compose = composer.getDeclaredMethod("compose", ItemStack.class, ItemStack.class, AttributeRuleSet.class);
        compose.setAccessible(true);
        primary = Class.forName(composer.getName() + "$Result").getDeclaredMethod("primary");
        primary.setAccessible(true);
        threshold = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredTool")
                .getMethod("getBlockBreakFECost");
        Class<?> ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        step = ability.getField("STEPHEIGHT").get(null);
        canUse = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool")
                .getMethod("canUseAbility", ItemStack.class, ability);
    }

    static boolean prepare(ItemStack root, AttributeRuleSet rules) throws ReflectiveOperationException {
        // A missing hook must retain generic defaults, not publish permanently powered armor.
        if (!hookSeen || !eligible(root)) { return false; }
        var data = root.get(ModContent.mixedBootsDataType().orElseThrow());
        List<ItemAttributeModifiers> states = new ArrayList<>();
        for (int left = 0; left < 3; left++) {
            for (int right = 0; right < 3; right++) {
                states.add(managedOnly(composed(view(data.left().source(), left), view(data.right().source(), right), rules)));
            }
        }
        var defaults = root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<ItemAttributeModifiers.Entry> baseline = new ArrayList<>(defaults.modifiers().stream()
                .filter(entry -> !managed(entry)).toList());
        baseline.addAll(states.get(0).modifiers());
        root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(baseline)));
        root.set(PROJECTIONS.get(), List.copyOf(states));
        return true;
    }

    static ItemAttributeModifiers composed(ItemStack left, ItemStack right, AttributeRuleSet rules)
            throws ReflectiveOperationException {
        // Reuse the real algebra at preparation only; no copied formulas or factories in queries.
        return (ItemAttributeModifiers) primary.invoke(compose.invoke(null, left, right, rules));
    }

    public static ItemAttributeModifiers project(ItemStack root, ItemAttributeModifiers defaults) {
        hookSeen = true;
        if (!(root.getItem() instanceof MixedBootsItem)) { return defaults; }
        var states = root.get(PROJECTIONS.get());
        if (states == null || !eligible(root)) { return defaults; }
        var data = root.get(ModContent.mixedBootsDataType().orElseThrow());
        int state;
        try { state = current(data.left().source(), root) * 3 + current(data.right().source(), root); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Bound native query failed", failure); }
        var baseline = states.get(0);
        var live = states.get(state);
        var keys = new LinkedHashSet<Key>();
        baseline.modifiers().forEach(entry -> keys.add(key(entry)));
        live.modifiers().forEach(entry -> keys.add(key(entry)));
        List<ItemAttributeModifiers.Entry> result = new ArrayList<>(defaults.modifiers());
        for (Key key : keys) {
            var expected = entries(baseline, key);
            if (!entries(defaults, key).equals(expected)) { continue; }
            result.removeIf(entry -> key(entry).equals(key));
            result.addAll(entries(live, key));
        }
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    /** A real earlier competing mixin changes only one identity, leaving the others routable. */
    public static ItemAttributeModifiers earlier(ItemStack root, ItemAttributeModifiers defaults) {
        if (!earlierOverride || !(root.getItem() instanceof MixedBootsItem)) { return defaults; }
        List<ItemAttributeModifiers.Entry> result = new ArrayList<>();
        for (var entry : defaults.modifiers()) {
            if (key(entry).attribute().equals(Identifier.withDefaultNamespace("armor"))) {
                result.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(entry.modifier().id(), 17,
                                entry.modifier().operation()), entry.slot(), entry.display()));
            } else { result.add(entry); }
        }
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    static DataComponentType<List<ItemAttributeModifiers>> projections() { return PROJECTIONS.get(); }

    private static boolean eligible(ItemStack root) {
        return root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && root.has(ModContent.mixedBootsDataType().orElseThrow())
                && JustDireResourceTestSupport.handler(root) != null;
    }

    private static boolean powered(ItemStack source) {
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.equals(Identifier.fromNamespaceAndPath("justdirethings", "celestigem_boots"))
                || id.equals(Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots"));
    }

    private static ItemStack view(ItemStack source, int state) {
        ItemStack view = source.copy();
        if (powered(source)) {
            view.set(JustDireResourceTestSupport.energyComponent(), state == 0 ? 0 : JustDireResourceTestSupport.handler(source).getCapacityAsInt());
            set(view, "tool_enabled", true);
            set(view, "stepheight_toggle", state == 2);
        }
        return view;
    }

    private static int current(ItemStack source, ItemStack root) throws ReflectiveOperationException {
        if (!powered(source)) { return 2; }
        int charge = root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
        if (charge < (int) threshold.invoke(source.getItem())) { return 0; }
        ItemStack view = source.copy();
        // The root can have the larger sibling tier's capacity; native probes remain valid tier stacks.
        view.set(JustDireResourceTestSupport.energyComponent(), Math.min(charge, JustDireResourceTestSupport.handler(source).getCapacityAsInt()));
        set(view, "tool_enabled", value(root, "tool_enabled", true));
        set(view, "stepheight_toggle", value(root, "stepheight_toggle", true));
        return (boolean) canUse.invoke(source.getItem(), view, step) ? 2 : 1;
    }

    @SuppressWarnings("unchecked")
    static <T> void set(ItemStack stack, String path, T value) {
        stack.set((DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value(), value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(ItemStack stack, String path, T fallback) {
        return stack.getOrDefault((DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value(), fallback);
    }

    private static boolean managed(ItemAttributeModifiers.Entry entry) {
        Key key = key(entry);
        return key.modifier().equals(Identifier.withDefaultNamespace("armor.boots"))
                && List.of("armor", "armor_toughness", "knockback_resistance").contains(key.attribute().getPath())
                && key.attribute().getNamespace().equals("minecraft")
                || key.attribute().equals(Identifier.withDefaultNamespace("step_height"))
                && key.modifier().equals(Identifier.fromNamespaceAndPath("justdirethings", "justdirestepassist"));
    }

    private static ItemAttributeModifiers managedOnly(ItemAttributeModifiers input) {
        return new ItemAttributeModifiers(input.modifiers().stream().filter(JustDireAttributeSpike::managed).toList());
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers input, Key key) {
        return input.modifiers().stream().filter(entry -> key(entry).equals(key)).toList();
    }

    private static Key key(ItemAttributeModifiers.Entry entry) {
        return new Key(BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()), entry.modifier().id());
    }

    private record Key(Identifier attribute, Identifier modifier) {}
}
