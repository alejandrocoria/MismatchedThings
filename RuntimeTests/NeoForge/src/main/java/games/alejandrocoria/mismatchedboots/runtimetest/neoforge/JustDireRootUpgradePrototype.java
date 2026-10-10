package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributeProjections;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Disposable ownership candidate. Never loaded in ordinary runs or shipped in production. */
public final class JustDireRootUpgradePrototype {
    static final List<String> ABILITIES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    private static final List<String> BOOTS = List.of("ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static Api api;

    private JustDireRootUpgradePrototype() {}

    public static void initialize(ItemStack root) {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (String ability : ABILITIES) {
            boolean installed = List.of(data.left().source(), data.right().source()).stream()
                    .anyMatch(source -> supports(source, ability) && installed(source, ability));
            root.remove(flag(ability));
            if (installed) { root.set(flag(ability), true); }
        }
    }

    public static void preserve(ItemStack previous, ItemStack output) {
        for (String ability : ABILITIES) {
            var type = flag(ability);
            output.remove(type);
            if (previous.has(type)) { output.set(type, previous.get(type)); }
        }
    }

    public static void overlay(ItemStack source, ItemStack root) {
        for (String ability : ABILITIES) { source.set(flag(ability), installed(root, ability)); }
    }

    static boolean installed(ItemStack stack, String ability) { return stack.getOrDefault(flag(ability), false); }

    static boolean supports(ItemStack source, String ability) {
        if (!nativeBoot(source)) { return false; }
        // Permanent tier eligibility must not discard installed data merely because use is config-disabled.
        try { return ((java.util.Set<?>) binding().allAbilities.invoke(source.getItem())).contains(binding().ability(ability)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static boolean nativeBoot(ItemStack source) {
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.getNamespace().equals("justdirethings") && BOOTS.contains(id.getPath());
    }

    private static boolean powered(ItemStack source) {
        return nativeBoot(source) && binding().powered.isInstance(source.getItem());
    }

    public static boolean handles(ItemStack root, ItemStack addition) {
        var id = BuiltInRegistries.ITEM.getKey(addition.getItem());
        return ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(root.getItem()))
                && id.getNamespace().equals("justdirethings")
                && ABILITIES.stream().anyMatch(ability -> id.getPath().equals("upgrade_" + ability));
    }

    public static Installation install(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        if (!handles(root, addition) || root.getCount() != 1 || ItemStack.validateStrict(root).error().isPresent()) { return null; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return null; }
        String ability = BuiltInRegistries.ITEM.getKey(addition.getItem()).getPath().substring("upgrade_".length());
        if (installed(root, ability)) { return null; }
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (!nativeBoot(source)) { continue; }
            overlay(source, root);
            var input = new SmithingRecipeInput(template.copy(), source, addition.copy());
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
            if (recipe.isEmpty() || !recipe.get().value().getClass().getName()
                    .equals("com.direwolf20.justdirethings.datagen.recipes.AbilityRecipe")) { continue; }
            var nativeResult = recipe.get().value().assemble(input);
            var expected = source.copy();
            expected.set(flag(ability), true);
            if (!ItemStack.matches(nativeResult, expected)) { continue; }
            ItemStack changed = root.copy();
            changed.set(flag(ability), true);
            // Same-source recomposition refreshes derived metadata but must not initialize a new wallet.
            var result = MixedBootsRecomposer.recompose(changed, data.left().source(), data.right().source());
            return new Installation(result, recipe.get());
        }
        return null;
    }

    public static void prepare(ItemStack previous, ItemStack root, AttributeRuleSet rules) {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        root.remove(JustDireAttributesCompatibility.projections());
        var sources = List.of(data.left().source(), data.right().source());
        if (sources.stream().noneMatch(JustDireRootUpgradePrototype::nativeBoot)) { return; }
        var states = new ArrayList<ItemAttributeModifiers>(9);
        for (int left = 0; left < 3; left++) { for (int right = 0; right < 3; right++) {
            var composed = JustDireAttributeTestSupport.composed(view(sources.getFirst(), root, left),
                    view(sources.getLast(), root, right), rules);
            states.add(new ItemAttributeModifiers(composed.modifiers().stream().filter(JustDireRootUpgradePrototype::managed).toList()));
        } }
        var table = new JustDireAttributeProjections(states);
        var defaults = root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        root.set(DataComponents.ATTRIBUTE_MODIFIERS, replace(defaults, null, table.states().getFirst()));
        root.set(JustDireAttributesCompatibility.projections(), table);
        if (previous != null && previous.has(JustDireAttributesCompatibility.projections())) {
            // Retain explicit root edits/removals instead of overwriting every managed identity.
            var oldBaseline = previous.get(JustDireAttributesCompatibility.projections()).states().getFirst();
            var oldActual = previous.get(DataComponents.ATTRIBUTE_MODIFIERS);
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, preserveEdits(root.get(DataComponents.ATTRIBUTE_MODIFIERS), oldBaseline, oldActual));
        }
    }

    public static ItemAttributeModifiers project(ItemStack root, ItemAttributeModifiers defaults) {
        var table = root.get(JustDireAttributesCompatibility.projections());
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (table == null || data == null || root.getCount() != 1) { return defaults; }
        int state = current(data.left().source(), root) * 3 + current(data.right().source(), root);
        return replace(defaults, table.states().getFirst(), table.states().get(state));
    }

    private static ItemStack view(ItemStack source, ItemStack root, int state) {
        if (!nativeBoot(source)) { return source; }
        overlay(source, root);
        source.set(flag("stepheight"), state == 2 && installed(root, "stepheight"));
        JustDireAttributeTestSupport.set(source, "tool_enabled", true);
        JustDireAttributeTestSupport.set(source, "stepheight_toggle", state == 2);
        if (powered(source)) {
            try { JustDireAttributeTestSupport.set(source, "forge_energy", state == 0 ? 0 : (int) binding().capacity.invoke(source.getItem())); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        return source;
    }

    private static int current(ItemStack source, ItemStack root) {
        if (!nativeBoot(source)) { return 1; }
        try {
            if (powered(source) && root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                    < (int) binding().threshold.invoke(source.getItem())) { return 0; }
            overlay(source, root);
            JustDireAttributeTestSupport.set(source, "tool_enabled", root.getOrDefault(component("tool_enabled"), true));
            JustDireAttributeTestSupport.set(source, "stepheight_toggle", root.getOrDefault(component("stepheight_toggle"), true));
            return (boolean) binding().canUse.invoke(source.getItem(), source, binding().ability("stepheight")) ? 2 : 1;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static ItemAttributeModifiers replace(ItemAttributeModifiers actual, ItemAttributeModifiers baseline, ItemAttributeModifiers live) {
        var result = new ArrayList<>(actual.modifiers());
        var keys = new java.util.LinkedHashSet<Key>();
        if (baseline != null) { baseline.modifiers().forEach(entry -> keys.add(key(entry))); }
        actual.modifiers().stream().filter(JustDireRootUpgradePrototype::managed).forEach(entry -> keys.add(key(entry)));
        live.modifiers().forEach(entry -> keys.add(key(entry)));
        for (var key : keys) {
            if (baseline != null && !entries(actual, key).equals(entries(baseline, key))) { continue; }
            result.removeIf(entry -> key(entry).equals(key));
            result.addAll(entries(live, key));
        }
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    private static ItemAttributeModifiers preserveEdits(ItemAttributeModifiers next, ItemAttributeModifiers baseline, ItemAttributeModifiers actual) {
        var result = new ArrayList<>(next.modifiers());
        var keys = new java.util.LinkedHashSet<Key>();
        baseline.modifiers().forEach(entry -> keys.add(key(entry)));
        actual.modifiers().stream().filter(JustDireRootUpgradePrototype::managed).forEach(entry -> keys.add(key(entry)));
        for (var key : keys) {
            if (entries(actual, key).equals(entries(baseline, key))) { continue; }
            result.removeIf(entry -> key(entry).equals(key));
            result.addAll(entries(actual, key));
        }
        return new ItemAttributeModifiers(List.copyOf(result));
    }

    private static List<ItemAttributeModifiers.Entry> entries(ItemAttributeModifiers modifiers, Key key) {
        return modifiers.modifiers().stream().filter(entry -> key(entry).equals(key)).toList();
    }

    private static boolean managed(ItemAttributeModifiers.Entry entry) {
        var key = key(entry);
        return key.modifier.equals(Identifier.withDefaultNamespace("armor.boots"))
                && List.of("armor", "armor_toughness", "knockback_resistance").contains(key.attribute.getPath())
                && key.attribute.getNamespace().equals("minecraft")
                || key.attribute.equals(Identifier.withDefaultNamespace("step_height"))
                && key.modifier.equals(Identifier.fromNamespaceAndPath("justdirethings", "justdirestepassist"));
    }

    private static Key key(ItemAttributeModifiers.Entry entry) {
        return new Key(BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()), entry.modifier().id());
    }

    @SuppressWarnings("unchecked") static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    static DataComponentType<Boolean> flag(String ability) { return component(ability + "_upgrade_installed"); }

    private static Api binding() {
        if (api == null) {
            try {
                var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
                var powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredTool");
                var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
                api = new Api(ability, powered, tool.getMethod("getAllAbilities"), tool.getMethod("canUseAbility", ItemStack.class, ability),
                        powered.getMethod("getMaxEnergy"), powered.getMethod("getBlockBreakFECost"));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        return api;
    }

    public record Installation(ItemStack output, RecipeHolder<SmithingRecipe> recipe) {}
    private record Key(Identifier attribute, Identifier modifier) {}
    private record Api(Class<?> abilities, Class<?> powered, Method allAbilities, Method canUse, Method capacity, Method threshold) {
        Object ability(String name) throws ReflectiveOperationException { return abilities.getField(name.toUpperCase(java.util.Locale.ROOT)).get(null); }
    }
}
