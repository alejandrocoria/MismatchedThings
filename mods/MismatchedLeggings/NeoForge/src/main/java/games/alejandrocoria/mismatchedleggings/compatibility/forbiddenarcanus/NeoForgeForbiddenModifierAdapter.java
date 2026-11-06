package games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
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
