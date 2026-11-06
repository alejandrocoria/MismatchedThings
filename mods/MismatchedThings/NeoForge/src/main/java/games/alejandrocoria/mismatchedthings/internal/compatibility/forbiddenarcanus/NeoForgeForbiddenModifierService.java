package games.alejandrocoria.mismatchedthings.internal.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public final class NeoForgeForbiddenModifierService implements ForbiddenModifierService {
    private static final Logger LOG = LoggerFactory.getLogger("mismatchedthings/forbidden_arcanus");
    private static final AtomicBoolean TITLE_WARNED = new AtomicBoolean();
    private static final Identifier COMPONENT = id("modifier");
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(id("item_modifier"));
    private static final ResourceKey<Object> ETERNAL = ResourceKey.create(REGISTRY, id("eternal"));
    private static final ResourceKey<Object> SOULBOUND = ResourceKey.create(REGISTRY, id("soulbound"));
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final String NATIVE_RECIPE = "com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe";

    @Override
    public ForbiddenModifiers read(ItemStack stack) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            return ForbiddenModifiers.EMPTY;
        }
        try {
            return readNative(stack);
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            throw new IllegalArgumentException("Could not read Forbidden Arcanus source modifiers", failure);
        }
    }

    @Override
    public ItemStack finish(ItemStack output, ForbiddenModifiers state, HolderLookup.@Nullable Provider registries) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            return output;
        }
        try {
            var type = nativeType();
            if (state.isEmpty()) {
                // Historical sources and configurable owner projection cannot install live effects.
                if (!readNative(output).isEmpty()) {
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
            throw new IllegalArgumentException("Could not finalize Forbidden Arcanus modifiers", failure);
        }
    }

    private static ForbiddenModifiers readNative(ItemStack stack) {
        var holder = stack.get(nativeType());
        if (holder == null) {
            return ForbiddenModifiers.EMPTY;
        }
        return new ForbiddenModifiers(holder.is(ETERNAL), holder.is(SOULBOUND));
    }

    @Override
    public SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition,
                                     ForbiddenModifiers current, List<ItemStack> sources) {
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
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
                var projection = root.get(nativeType());
                if (current.union(added).equals(current) || api.excludes(root, modifier)
                        || projection != null && !projection.is(ETERNAL) && !projection.is(SOULBOUND)) {
                    continue;
                }
                for (var source : sources) {
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
                    return new SmithingDecision(true, Optional.of(new SmithingMatch(recipe, current.union(added))));
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
            LOG.warn("Forbidden Arcanus modifier operation refused: {}", failure.toString());
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

    @Override
    public Component title(Component original, ForbiddenModifiers state, HolderLookup.Provider registries) {
        if (state.isEmpty() || !ModList.get().isLoaded("forbidden_arcanus")) {
            return original;
        }
        try {
            var names = Names.INSTANCE;
            if (names == null) {
                return original;
            }
            var registry = registries.lookupOrThrow(REGISTRY);
            var title = Component.empty().withStyle(original.getStyle());
            for (String kind : new String[]{"eternal", "soulbound"}) {
                if (kind.equals("eternal") ? state.eternal() : state.soulbound()) {
                    var modifier = registry.getOrThrow(ResourceKey.create(REGISTRY, id(kind))).value();
                    title.append(((Component) names.name().invoke(names.display().invoke(modifier))).copy()).append(" ");
                }
            }
            return title.append(original.copy());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnTitle(failure);
            return original;
        }
    }

    private static void warnTitle(Throwable failure) {
        if (TITLE_WARNED.compareAndSet(false, true)) {
            LOG.warn("Forbidden Arcanus modifier tooltip title unavailable: {}", failure.toString());
        }
    }

    /** Resolve methods lazily, never retain world registries or native modifier instances. */
    private record Names(Method display, Method name) {
        private static final Names INSTANCE = resolve();

        private static Names resolve() {
            try {
                var display = Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier")
                        .getMethod("displaySettings");
                var name = display.getReturnType().getMethod("name");
                if (name.getReturnType() != Component.class) {
                    throw new NoSuchMethodException("Unexpected native modifier display name");
                }
                return new Names(display, name);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                warnTitle(failure);
                return null;
            }
        }
    }
}
