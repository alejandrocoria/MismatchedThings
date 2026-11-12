package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
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
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native installation flags are live root state; historical source flags are imported only at creation. */
public final class JustDireState implements JustDireComposition {
    private static final List<String> NAMES = List.of("walkspeed", "runspeed", "swimspeed", "decoy", "phase");
    private static final List<String> ITEMS = List.of("ferricore_leggings", "blazegold_leggings", "celestigem_leggings", "eclipsealloy_leggings");
    private static final AtomicBoolean RECIPE_WARNED = new AtomicBoolean();
    private static volatile Api api;
    private static volatile boolean recipeFailed;

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) {
            bus.addListener(JustDireState::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var items = ITEMS.stream().map(path -> BuiltInRegistries.ITEM.get(id(path)).orElseThrow().value()).toList();
            if (items.stream().anyMatch(item -> !tool.isInstance(item))) {
                throw new IllegalStateException("Unsupported native leggings upgrade API");
            }
            var upgrades = new ArrayList<Upgrade>();
            for (String name : NAMES) {
                DataComponentType<Boolean> flag = component(name + "_upgrade_installed");
                if (flag.codec() == null || flag.streamCodec() == null
                        || !(flag.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(false)).getOrThrow() instanceof Boolean)) {
                    throw new IllegalStateException("Unsupported installed component: " + name);
                }
                upgrades.add(new Upgrade(name, flag, ability.getField(name.toUpperCase(Locale.ROOT)).get(null)));
            }
            api = new Api(items, List.copyOf(upgrades), MethodHandles.publicLookup()
                    .findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                    .asType(MethodType.methodType(Set.class, Item.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not bind Just Dire Things leggings upgrades; root installation disabled", failure);
        }
    }

    @Override
    public void prepare(@Nullable ItemStack previous, ItemStack output, MixedLeggingsData data) {
        if (previous == null) {
            initializeUpgrades(output, data);
        } else {
            preserveControls(previous, output);
        }
        JustDireResources.prepare(previous, output, data);
    }

    private static void initializeUpgrades(ItemStack output, MixedLeggingsData data) {
        Api binding = api;
        var sources = List.of(data.left().source(), data.right().source());
        if (binding == null) {
            if (sources.stream().anyMatch(JustDireState::nativeIdentity)) {
                throw new IllegalArgumentException("Cannot initialize Just Dire Things root upgrades without their native API");
            }
            return;
        }
        var imported = new ArrayList<DataComponentType<Boolean>>();
        try {
            for (var source : sources) {
                if (!binding.items.contains(source.getItem())) {
                    continue;
                }
                Set<?> abilities = (Set<?>) binding.allAbilities.invokeExact(source.getItem());
                for (var upgrade : binding.upgrades) {
                    if (abilities.contains(upgrade.ability) && source.getOrDefault(upgrade.flag, false)) {
                        imported.add(upgrade.flag);
                    }
                }
            }
        } catch (Throwable failure) {
            disable(binding, failure);
            throw new IllegalArgumentException("Cannot import native Just Dire Things installations", failure);
        }
        if (api != binding) {
            throw new IllegalArgumentException("Just Dire Things upgrade binding changed during creation");
        }
        binding.upgrades.forEach(upgrade -> output.remove(upgrade.flag));
        imported.forEach(flag -> output.set(flag, true));
        // Explicit user controls follow the owner, not a configured numeric/boolean projection.
        preserveControls(data.left().source(), output, false);
    }

    private static void preserveControls(ItemStack previous, ItemStack output) {
        preserveControls(previous, output, true);
    }

    private static void preserveControls(ItemStack previous, ItemStack output, boolean installations) {
        for (String name : NAMES) {
            if (installations) {
                preserve(previous, output, name + "_upgrade_installed");
            }
            preserve(previous, output, name + "_toggle");
            preserve(previous, output, name + "_value");
            preserve(previous, output, name + "_bindingmode");
        }
        for (String path : List.of("tool_enabled", "ability_bindings", "ability_cooldowns", "lavarepair_toggle", "lavarepair_bindingmode")) {
            preserve(previous, output, path);
        }
    }

    private static void preserve(ItemStack previous, ItemStack output, String path) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).ifPresent(holder -> preserve(previous, output, holder.value()));
    }

    private static <T> void preserve(ItemStack previous, ItemStack output, DataComponentType<T> type) {
        output.remove(type);
        if (previous.has(type)) {
            output.set(type, previous.get(type));
        }
    }

    @Override
    public SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var additionId = BuiltInRegistries.ITEM.getKey(addition.getItem());
        String name = additionId.getNamespace().equals("justdirethings") ? NAMES.stream()
                .filter(value -> additionId.getPath().equals("upgrade_" + value)).findFirst().orElse(null) : null;
        if (name == null) {
            return SmithingDecision.PASS;
        }
        // Handled refusal must not fall through to installation on historical source copies.
        Api binding = api;
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (binding == null || data == null || recipeFailed) {
            return SmithingDecision.DENIED;
        }
        var upgrade = binding.upgrades.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
        if (root.getOrDefault(upgrade.flag, false)) {
            return SmithingDecision.DENIED;
        }
        try {
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (!binding.items.contains(source.getItem())) {
                    continue;
                }
                binding.upgrades.forEach(value -> source.set(value.flag, root.getOrDefault(value.flag, false)));
                var input = new SmithingRecipeInput(template.copy(), source, addition.copy());
                var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
                if (recipe.isEmpty() || !recipe.orElseThrow().value().getClass().getName()
                        .equals("com.direwolf20.justdirethings.datagen.recipes.AbilityRecipe")) {
                    continue;
                }
                var assembled = recipe.orElseThrow().value().assemble(input);
                if (assembled.isEmpty()) {
                    continue;
                }
                var expected = source.copy();
                expected.set(upgrade.flag, true);
                if (!ItemStack.matches(expected, assembled)) {
                    recipeFailed = true;
                    if (RECIPE_WARNED.compareAndSet(false, true)) {
                        LeggingsContent.LOG.warn("Just Dire Things leggings upgrade recipe returned an unsupported result; installation rejected");
                    }
                    return SmithingDecision.DENIED;
                }
                var changed = root.copy();
                changed.set(upgrade.flag, true);
                // A root-only installation must not rebuild unrelated source-derived components.
                var output = changed.copy();
                prepare(changed, output, data);
                if (MixedLeggingsStructure.validData(output).isEmpty()) {
                    return SmithingDecision.DENIED;
                }
                return new SmithingDecision(true, Optional.of(new LeggingsSmithing.Result(output, recipe.orElseThrow(), null)));
            }
        } catch (IllegalArgumentException rejected) {
            LeggingsContent.LOG.debug("Rejected Just Dire Things leggings root smithing: {}", rejected.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            recipeFailed = true;
            if (RECIPE_WARNED.compareAndSet(false, true)) {
                LeggingsContent.LOG.warn("Just Dire Things leggings recipe evaluation failed; root installation disabled", failure);
            }
        }
        return SmithingDecision.DENIED;
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("justdirethings", path);
    }

    private static boolean nativeIdentity(ItemStack source) {
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.getNamespace().equals("justdirethings") && ITEMS.contains(id.getPath());
    }

    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).orElseThrow().value();
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
            throw fatal;
        }
        synchronized (JustDireState.class) {
            if (api == binding) {
                api = null;
                LeggingsContent.LOG.warn("Just Dire Things leggings upgrade query failed; installation disabled", failure);
            }
        }
    }

    private record Upgrade(String name, DataComponentType<Boolean> flag, Object ability) {}

    private record Api(List<Item> items, List<Upgrade> upgrades, MethodHandle allAbilities) {}
}
