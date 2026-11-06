package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Disposable candidate, not a product migration or a Things API. No native API is bound at codec construction. */
public final class ForbiddenBootsCandidate {
    static final Identifier BOOTS = Identifier.parse("mismatchedboots:mixed_boots");
    static final DataComponentType<ForbiddenModifiers> FLAGS = DataComponentType.<ForbiddenModifiers>builder()
            .persistent(ForbiddenModifiers.CODEC).networkSynchronized(ForbiddenModifiers.STREAM_CODEC).build();
    static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    static int adoptions;
    static int nativeApplications;

    private ForbiddenBootsCandidate() {}

    public static ItemStack adopt(ItemStack root) {
        if (BYPASS.get() || !valid(root) || root.has(FLAGS)) {
            return root;
        }
        var holder = nativeHolder(root);
        var state = state(holder);
        if (state.isEmpty()) {
            return root;
        }
        var base = Component.translatable("item.mismatchedboots.mixed_boots");
        var control = new ItemStack(root.getItem());
        control.set(DataComponents.ITEM_NAME, base);
        apply(control, holder);
        var stored = root.get(DataComponents.ITEM_NAME);
        if (!base.equals(stored) && !control.get(DataComponents.ITEM_NAME).equals(stored)) {
            return root;
        }
        var before = root.copy();
        var result = root.copy();
        result.set(FLAGS, state);
        apply(result, holder);
        result.set(DataComponents.ITEM_NAME, base);
        require(ItemStack.matches(before, root), "Load conversion mutated its input or source history");
        adoptions++;
        return result;
    }

    static boolean valid(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || !BOOTS.equals(BuiltInRegistries.ITEM.getKey(root.getItem()))
                || root.getMaxStackSize() != 1 || !BOOTS.equals(root.get(DataComponents.ITEM_MODEL))
                || ItemStack.validateStrict(root).isError()) {
            return false;
        }
        var suspended = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("mismatchedboots:suspended_boots"));
        var equipment = root.get(DataComponents.EQUIPPABLE);
        if (suspended != null && root.has(suspended) || equipment == null || equipment.slot() != EquipmentSlot.FEET) {
            return false;
        }
        return sources(root).size() == 2;
    }

    static List<ItemStack> sources(ItemStack root) {
        try {
            var content = Class.forName("games.alejandrocoria.mismatchedboots.content.ModContent");
            var maybe = (java.util.Optional<?>) content.getMethod("getMixedBootsData", ItemStack.class).invoke(null, root);
            if (maybe.isEmpty()) { return List.of(); }
            var data = maybe.orElseThrow();
            var a = data.getClass().getMethod("left").invoke(data);
            var b = data.getClass().getMethod("right").invoke(data);
            var first = (ItemStack) a.getClass().getMethod("source").invoke(a);
            var second = (ItemStack) b.getClass().getMethod("source").invoke(b);
            var admission = a.getClass().getMethod("isValidSource", ItemStack.class);
            return (boolean) admission.invoke(null, first) && (boolean) admission.invoke(null, second)
                    ? List.of(first, second) : List.of();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Old Boots source boundary unavailable", failure);
        }
    }

    @SuppressWarnings("unchecked")
    static DataComponentType<Holder<Object>> nativeType() {
        return (DataComponentType<Holder<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(Identifier.parse("forbidden_arcanus:modifier"));
    }

    static Holder<Object> nativeHolder(ItemStack root) {
        return root.get(nativeType());
    }

    static ForbiddenModifiers state(Holder<?> holder) {
        String key = holder == null ? "" : holder.unwrapKey().orElseThrow().identifier().toString();
        return new ForbiddenModifiers(key.equals("forbidden_arcanus:eternal"), key.equals("forbidden_arcanus:soulbound"));
    }

    static Holder<Object> modifier(MinecraftServer server, String kind) {
        ResourceKey<Registry<Object>> registry = ResourceKey.createRegistryKey(Identifier.parse("forbidden_arcanus:item_modifier"));
        return server.registryAccess().lookupOrThrow(registry)
                .getOrThrow(ResourceKey.create(registry, Identifier.fromNamespaceAndPath("forbidden_arcanus", kind)));
    }

    static void apply(ItemStack root, Holder<?> holder) {
        try {
            nativeApplications++;
            Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper")
                    .getMethod("setModifier", ItemStack.class, Holder.class).invoke(null, root, holder);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Native modifier application failed", failure);
        }
    }
}
