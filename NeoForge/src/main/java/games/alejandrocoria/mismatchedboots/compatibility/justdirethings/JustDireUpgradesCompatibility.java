package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Five root-owned installations, not a native tool identity or a second resource wallet. */
public final class JustDireUpgradesCompatibility {
    private static final List<String> ABILITIES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    private static final List<String> BOOTS = List.of("ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static volatile Api api;
    private static boolean recipeShapeWarned;

    private JustDireUpgradesCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireUpgradesCompatibility::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var boots = BOOTS.stream().map(path -> BuiltInRegistries.ITEM.get(id(path)).orElseThrow().value()).toList();
            if (boots.stream().anyMatch(item -> !tool.isInstance(item))) { throw new IllegalStateException("Unsupported native boot upgrade API"); }
            var upgrades = new ArrayList<Upgrade>();
            for (String name : ABILITIES) {
                DataComponentType<Boolean> flag = component(name + "_upgrade_installed");
                if (flag.codec() == null || !(flag.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(false)).getOrThrow() instanceof Boolean)) {
                    throw new IllegalStateException("Unsupported native installed component: " + name);
                }
                upgrades.add(new Upgrade(name, flag, ability.getField(name.toUpperCase(java.util.Locale.ROOT)).get(null)));
            }
            api = new Api(boots, List.copyOf(upgrades), MethodHandles.publicLookup()
                    .findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                    .asType(MethodType.methodType(Set.class, Item.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things upgrade API; root upgrade compatibility disabled", failure);
        }
    }

    public static boolean available() { return api != null; }

    public static boolean nativeBoot(ItemStack source) {
        Api binding = api;
        return binding != null && binding.boots.contains(source.getItem());
    }

    public static void initializeUpgrades(ItemStack output) {
        Api binding = api;
        if (binding == null) { return; }
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        var imported = new ArrayList<DataComponentType<Boolean>>();
        try {
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (!binding.boots.contains(source.getItem())) { continue; }
                Set<?> permanent = (Set<?>) binding.allAbilities.invokeExact(source.getItem());
                for (var upgrade : binding.upgrades) {
                    if (permanent.contains(upgrade.ability) && source.getOrDefault(upgrade.flag, false)) { imported.add(upgrade.flag); }
                }
            }
        } catch (Throwable failure) { disable(binding, failure); return; }
        if (api != binding) { return; }
        // Publish only after every optional read succeeds. Config-disabled flags remain installed.
        binding.upgrades.forEach(upgrade -> output.remove(upgrade.flag));
        imported.forEach(flag -> output.set(flag, true));
    }

    public static void preserveState(ItemStack previous, ItemStack output) {
        // Registry-only preservation also works after an optional binding fails. Never reimport sources.
        for (String ability : ABILITIES) {
            preserve(previous, output, ability + "_upgrade_installed");
            preserve(previous, output, ability + "_toggle");
            preserve(previous, output, ability + "_value");
        }
        for (String ability : List.of("stepheight", "jumpboost", "negatefalldamage")) {
            preserve(previous, output, ability + "_bindingmode");
        }
        preserve(previous, output, "tool_enabled");
        // Live active state is never source-owned again, even when equal to an old projection.
        for (String path : List.of("ability_bindings", "groundstomp_bindingmode", "groundstomp_custom_setting", "ability_cooldowns")) {
            preserve(previous, output, path);
        }
    }

    private static void preserve(ItemStack previous, ItemStack output, String path) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).ifPresent(holder -> preserve(previous, output, holder.value()));
    }

    private static <T> void preserve(ItemStack previous, ItemStack output, DataComponentType<T> type) {
        output.remove(type);
        if (previous.has(type)) { output.set(type, previous.get(type)); }
    }

    /** Only defensive native query copies may receive these flags; no source snapshot is mutated. */
    public static boolean overlayInstallation(ItemStack source, ItemStack root) {
        Api binding = api;
        if (binding == null) { return false; }
        binding.upgrades.forEach(upgrade -> source.set(upgrade.flag, root.getOrDefault(upgrade.flag, false)));
        return true;
    }

    public static MixedBootsSmithingPolicy.RootAttempt smithing(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var additionId = BuiltInRegistries.ITEM.getKey(addition.getItem());
        if (!additionId.getNamespace().equals("justdirethings")) { return MixedBootsSmithingPolicy.RootAttempt.UNHANDLED; }
        String name = ABILITIES.stream().filter(ability -> additionId.getPath().equals("upgrade_" + ability)).findFirst().orElse(null);
        if (name == null) { return MixedBootsSmithingPolicy.RootAttempt.UNHANDLED; }
        Api binding = api;
        // Even failed binding/duplicate/config rejection must not install on a historical source.
        if (binding == null) { return MixedBootsSmithingPolicy.RootAttempt.REJECTED; }
        var upgrade = binding.upgrades.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
        if (root.getOrDefault(upgrade.flag, false)) { return MixedBootsSmithingPolicy.RootAttempt.REJECTED; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (!binding.boots.contains(source.getItem()) || !overlayInstallation(source, root)) { continue; }
            var input = new SmithingRecipeInput(template.copy(), source, addition.copy());
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
            if (recipe.isEmpty() || !recipe.get().value().getClass().getName()
                    .equals("com.direwolf20.justdirethings.datagen.recipes.AbilityRecipe")) { continue; }
            var assembled = recipe.get().value().assemble(input);
            if (assembled.isEmpty()) { continue; } // Native tier/config/installed checks own rejection.
            var expected = source.copy();
            expected.set(upgrade.flag, true);
            if (!ItemStack.matches(expected, assembled)) {
                synchronized (JustDireUpgradesCompatibility.class) {
                    if (!recipeShapeWarned) {
                        recipeShapeWarned = true;
                        Constants.LOG.warn("Just Dire Things upgrade recipe returned an unsupported result; root installation rejected");
                    }
                }
                return MixedBootsSmithingPolicy.RootAttempt.REJECTED;
            }
            var changed = root.copy();
            changed.set(upgrade.flag, true);
            try {
                var output = MixedBootsRecomposer.recompose(changed, data.left().source(), data.right().source());
                return new MixedBootsSmithingPolicy.RootAttempt(true, new MixedBootsSmithingPolicy.Result(output, recipe.get(), null));
            } catch (IllegalArgumentException rejected) {
                Constants.LOG.debug("Rejected root-upgrade recomposition: {}", rejected.getMessage());
                return MixedBootsSmithingPolicy.RootAttempt.REJECTED;
            }
        }
        return MixedBootsSmithingPolicy.RootAttempt.REJECTED;
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        synchronized (JustDireUpgradesCompatibility.class) {
            if (api == binding) {
                api = null;
                Constants.LOG.warn("Just Dire Things installed-upgrade query failed; root upgrade compatibility disabled", failure);
            }
        }
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("justdirethings", path); }
    @SuppressWarnings("unchecked") private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).orElseThrow().value();
    }
    private record Upgrade(String name, DataComponentType<Boolean> flag, Object ability) {}
    private record Api(List<Item> boots, List<Upgrade> upgrades, MethodHandle allAbilities) {}
}
