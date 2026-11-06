package games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Exact Eternal/Soulbound integration; all native application happens on disposable output copies. */
public final class NeoForgeForbiddenModifierAdapter implements ForbiddenModifierAdapter {
    private static final Identifier COMPONENT = id("modifier");
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(id("item_modifier"));
    private static final ResourceKey<Object> ETERNAL = ResourceKey.create(REGISTRY, id("eternal"));
    private static final ResourceKey<Object> SOULBOUND = ResourceKey.create(REGISTRY, id("soulbound"));
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final String NATIVE_RECIPE = "com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe";

    @Override
    public ForbiddenModifiers inherit(ItemStack left, ItemStack right) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            return ForbiddenModifiers.EMPTY;
        }
        try {
            return read(left).union(read(right));
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            throw new IllegalArgumentException("Could not read Forbidden Arcanus source modifiers", failure);
        }
    }

    @Override
    public ItemStack finish(ItemStack output, HolderLookup.@Nullable Provider registries) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            return output;
        }
        try {
            var state = output.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
            var type = nativeType();
            if (state.isEmpty()) {
                // Historical sources and configurable owner projection cannot install live effects.
                if (!read(output).isEmpty()) {
                    output.remove(type);
                }
                return output;
            }
            if (registries == null) {
                throw new IllegalArgumentException("Forbidden Arcanus composition requires the operation's registry context");
            }
            var api = NativeApi.INSTANCE;
            if (api == null) {
                throw new IllegalArgumentException("Incompatible Forbidden Arcanus modifier API");
            }
            var lookup = registries.lookupOrThrow(REGISTRY);
            var result = output.copy();
            var name = output.get(DataComponents.ITEM_NAME);
            if (state.eternal()) {
                var eternal = lookup.getOrThrow(ETERNAL);
                api.apply(result, eternal, Set.of(DataComponents.MAX_DAMAGE, DataComponents.DAMAGE));
            }
            if (state.soulbound()) {
                // Native death/respawn only sees this scalar field. Eternal's removal already ran.
                api.apply(result, lookup.getOrThrow(SOULBOUND), Set.of());
            }
            if (result.get(type) != lookup.getOrThrow(state.soulbound() ? SOULBOUND : ETERNAL)
                    || !state.equals(result.get(LeggingsContent.forbiddenModifiersType()))
                    || !Objects.equals(output.get(DataComponents.CUSTOM_NAME), result.get(DataComponents.CUSTOM_NAME))
                    || state.eternal() && (result.has(DataComponents.MAX_DAMAGE) || result.has(DataComponents.DAMAGE))) {
                throw new IllegalArgumentException("Native modifier application did not preserve the root effect contract");
            }
            if (name == null) {
                result.remove(DataComponents.ITEM_NAME);
            } else {
                result.set(DataComponents.ITEM_NAME, name);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
            throw new IllegalArgumentException("Could not finalize Forbidden Arcanus mixed leggings modifiers", failure);
        }
    }

    private static ForbiddenModifiers read(ItemStack stack) {
        var holder = stack.get(nativeType());
        if (holder == null) {
            return ForbiddenModifiers.EMPTY;
        }
        return new ForbiddenModifiers(holder.is(ETERNAL), holder.is(SOULBOUND));
    }

    @Override
    public SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            return SmithingDecision.PASS;
        }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return SmithingDecision.PASS;
        }
        boolean handled = false;
        try {
            for (var recipe : level.getServer().getRecipeManager().recipeMap().byType(RecipeType.SMITHING)) {
                var nativeRecipe = recipe.value();
                if (!nativeRecipe.getClass().getName().equals(NATIVE_RECIPE)
                        || !Ingredient.testOptionalIngredient(nativeRecipe.templateIngredient(), template)
                        || !Ingredient.testOptionalIngredient(nativeRecipe.additionIngredient(), addition)) {
                    continue;
                }
                var direct = level.recipeAccess().getRecipeFor(RecipeType.SMITHING,
                        new SmithingRecipeInput(template.copy(), root.copy(), addition.copy()), level).orElse(null);
                // An unrelated dedicated recipe selected by vanilla still owns its root result.
                if (direct != null && !direct.value().getClass().getName().equals(NATIVE_RECIPE)) {
                    return SmithingDecision.PASS;
                }
                var api = NativeRecipes.INSTANCE;
                if (api == null) {
                    return SmithingDecision.DENIED;
                }
                var modifier = api.modifier(nativeRecipe);
                var added = new ForbiddenModifiers(modifier.is(ETERNAL), modifier.is(SOULBOUND));
                if (added.isEmpty()) {
                    continue;
                }
                handled = true;
                var current = root.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
                var projection = root.get(nativeType());
                if (current.union(added).equals(current) || api.excludes(root, modifier)
                        || projection != null && !projection.is(ETERNAL) && !projection.is(SOULBOUND)) {
                    continue;
                }
                for (var source : List.of(data.left().source(), data.right().source())) {
                    // Borrow only native item/tag admission. Live enchantments, damage, custom data
                    // and the own flags remain the root's; historical modifiers never authorize it.
                    var admission = new ItemStack(source.getItem());
                    List.copyOf(admission.getComponents().keySet()).forEach(admission::remove);
                    admission.applyComponents(root.getComponents());
                    // Audited predicates use damage/armor tags, not this scalar field. Mask its
                    // one-modifier limit on the private view, never on the root or its sources.
                    admission.remove(nativeType());
                    if (!nativeRecipe.matches(new SmithingRecipeInput(template.copy(), admission, addition.copy()), level)) {
                        continue;
                    }
                    var output = root.copy();
                    output.set(LeggingsContent.forbiddenModifiersType(), current.union(added));
                    output = finish(output, level.registryAccess());
                    if (MixedLeggingsStructure.validData(output).isEmpty()) {
                        throw new IllegalArgumentException("Modifier smithing changed mixed leggings structure");
                    }
                    return new SmithingDecision(true, Optional.of(new LeggingsSmithing.Result(output, recipe, null)));
                }
            }
            return handled ? SmithingDecision.DENIED : SmithingDecision.PASS;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
            return SmithingDecision.DENIED;
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Holder<Object>> nativeType() {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(COMPONENT);
        if (type == null) {
            throw new IllegalArgumentException("Forbidden Arcanus modifier component is absent");
        }
        return (DataComponentType<Holder<Object>>) type;
    }

    private static void warn(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            LeggingsContent.LOG.warn("Forbidden Arcanus mixed leggings modifier composition refused: {}", failure.toString());
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("forbidden_arcanus", path);
    }

    /** Cache field shape, never a recipe holder across reloads. Subclasses are not assumed equivalent. */
    private record NativeRecipes(Field modifier, Method incompatibleItems) {
        private static final NativeRecipes INSTANCE = resolve();

        private static @Nullable NativeRecipes resolve() {
            try {
                var type = Class.forName(NATIVE_RECIPE);
                var field = type.getDeclaredField("modifier");
                if (field.getType() != Holder.class || Modifier.isStatic(field.getModifiers())) {
                    throw new NoSuchFieldException("Unexpected native modifier recipe field");
                }
                field.setAccessible(true);
                var exclusions = Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier")
                        .getMethod("incompatibleItems");
                if (exclusions.getReturnType() != HolderSet.class || Modifier.isStatic(exclusions.getModifiers())) {
                    throw new NoSuchMethodException("Unexpected native item exclusion method");
                }
                return new NativeRecipes(field, exclusions);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                warn(failure);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private Holder<Object> modifier(SmithingRecipe recipe) throws IllegalAccessException {
            return (Holder<Object>) modifier.get(recipe);
        }

        @SuppressWarnings("unchecked")
        private boolean excludes(ItemStack root, Holder<Object> modifier) throws ReflectiveOperationException {
            return root.is((HolderSet<Item>) incompatibleItems.invoke(modifier.value()));
        }
    }

    /** Cache API shape only, never a datapack definition/holder or client/server registry. */
    private record NativeApi(Method setModifier, Method componentsToRemove) {
        private static final NativeApi INSTANCE = resolve();

        private static @Nullable NativeApi resolve() {
            try {
                var modifier = Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier");
                var helper = Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper");
                var apply = helper.getMethod("setModifier", ItemStack.class, Holder.class);
                var removals = modifier.getMethod("componentsToRemove");
                if (apply.getReturnType() != void.class || !Modifier.isStatic(apply.getModifiers())
                        || removals.getReturnType() != HolderSet.class || Modifier.isStatic(removals.getModifiers())) {
                    throw new NoSuchMethodException("Unexpected native modifier method signature");
                }
                return new NativeApi(apply, removals);
            } catch (ReflectiveOperationException | LinkageError failure) {
                warn(failure);
                return null;
            }
        }

        private void apply(ItemStack output, Holder<Object> holder, Set<DataComponentType<?>> expectedRemovals)
                throws ReflectiveOperationException {
            var removals = (HolderSet<?>) componentsToRemove.invoke(holder.value());
            if (!removals.stream().map(Holder::value).collect(Collectors.toSet()).equals(expectedRemovals)) {
                throw new IllegalArgumentException("Unsupported component removals for " + holder.unwrapKey().orElseThrow());
            }
            setModifier.invoke(null, output, holder);
        }
    }
}
