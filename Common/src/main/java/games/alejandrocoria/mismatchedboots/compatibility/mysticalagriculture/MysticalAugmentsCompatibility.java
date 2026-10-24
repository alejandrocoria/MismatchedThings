package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stateless source entitlement view; native MA retains all player effect/cache ownership. */
public final class MysticalAugmentsCompatibility {
    static final String PREFIX = "com.blakebr0.mysticalagriculture.";
    static final String TICK_PROOF = "mismatchedboots$maArmorTickReady";
    static final String EQUIPMENT_PROOF = "mismatchedboots$maEquipmentReady";
    private static final Set<MysticalAugments.Family> DISABLED = ConcurrentHashMap.newKeySet();
    private static volatile Binding binding;
    private static volatile boolean attempted;

    private MysticalAugmentsCompatibility() {}

    public static List<Identifier> selectedIds(ItemStack root) {
        return selected(root).stream().map(MysticalAugments.Spec::id).toList();
    }

    private static boolean nativeBoot(ItemStack source) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.getNamespace().equals("mysticalagriculture") && MysticalAugments.BOOTS.contains(id.getPath());
    }

    private static List<MysticalAugments.Spec> selected(ItemStack root) {
        if (root == null || !root.is(ModContent.MIXED_BOOTS_KEY)) { return List.of(); }
        var equippable = root.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.FEET
                || !MixedBootsSources.anySource(root, MysticalAugmentsCompatibility::nativeBoot)) { return List.of(); }
        Binding api = bindings();
        if (api == null) { return List.of(); }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        return MysticalAugments.select(installed(data.left().source(), api), installed(data.right().source(), api))
                .stream().filter(spec -> available(spec.family(), api)).toList();
    }

    /** Physical occupied entries are not deduplicated here, independently of effect readiness. */
    private static List<MysticalAugments.Spec> installed(ItemStack source, Binding api) {
        if (!nativeBoot(source) || source.getItem().getClass() != api.bootType) { return List.of(); }
        try {
            int slots = (int) api.slots.invoke(source.getItem());
            int tier = (int) api.tier.invoke(source.getItem());
            if (slots < 1 || slots > 2 || tier < 1 || tier > 5) { return List.of(); }
            Object component = source.get(api.component);
            if (!(component instanceof List<?> entries) || entries.size() > slots) { return List.of(); }
            boolean[] occupied = new boolean[slots];
            var result = new ArrayList<MysticalAugments.Spec>();
            for (Object entry : entries) {
                if (entry == null || entry.getClass() != api.entryType) { return List.of(); }
                int slot = (int) api.slot.invoke(entry);
                if (slot < 0 || slot >= slots || occupied[slot]) { return List.of(); }
                occupied[slot] = true;
                Identifier id = (Identifier) api.id.invoke(entry);
                MysticalAugments.Spec spec = MysticalAugments.CATALOG.get(id);
                if (spec == null) { continue; }
                Object augment = api.augments.get(id);
                if (augment != null && Boolean.TRUE.equals(api.canApply.invoke(source.getItem(), augment))) { result.add(spec); }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return List.of(); // A malformed/foreign source is not authorization; never repair its data here.
        }
    }

    /** Only left-source purchases belong to this output; never consult the effective union/root copy. */
    static RefundView refunds(ItemStack root) {
        if (root == null || !root.is(ModContent.MIXED_BOOTS_KEY)
                || !MixedBootsSources.anySource(root, MysticalAugmentsCompatibility::nativeBoot)) { return null; }
        Binding api = bindings();
        if (api == null) { return null; }
        ItemStack source = ModContent.getMixedBootsData(root).orElseThrow().left().source();
        List<MysticalAugments.Spec> purchases = installed(source, api);
        if (purchases.isEmpty()) { return null; }
        try {
            Method getItem = Class.forName(PREFIX + "api.tinkering.Augment").getMethod("getItem");
            var items = new ArrayList<Item>();
            for (var spec : purchases) {
                Object value = getItem.invoke(api.augments.get(spec.id()));
                if (!(value instanceof Item item) || item == Items.AIR
                        || BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(item)).orElseThrow().value() != item) { return null; }
                items.add(item);
            }
            return new RefundView(api, source.getItem(), List.copyOf((List<?>) source.get(api.component)), List.copyOf(items));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            MysticalRefundCompatibility.unavailable(failure); return null;
        }
    }

    record RefundView(Object binding, Item sourceItem, List<?> installed, List<Item> items) {}

    public static List<Object> equipment(Player player, ItemStack stack, List<Object> ordinary) {
        if (player.getItemBySlot(EquipmentSlot.FEET) != stack) { return ordinary; }
        List<MysticalAugments.Spec> specs = selected(stack);
        if (specs.isEmpty()) { return ordinary; }
        var result = new ArrayList<>(ordinary);
        for (var spec : specs) { result.add(binding.augments.get(spec.id())); }
        return result;
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.FEET || !(entity instanceof Player player) || player.getItemBySlot(slot) != root) { return; }
        for (var spec : selected(root)) {
            if (spec.family().route != MysticalAugments.Route.ARMOR) { continue; }
            try { binding.armorTick.invoke(binding.augments.get(spec.id()), root, level, player); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error error && !(error instanceof LinkageError)) { throw error; }
                decline(spec.family(), cause); // No rollback, fallback receiver or retry after native side effects.
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { decline(spec.family(), failure); }
        }
    }

    private static boolean available(MysticalAugments.Family family, Binding api) {
        return !DISABLED.contains(family) && (family.route == MysticalAugments.Route.ARMOR
                ? api.tickReady.contains(family) : api.equipmentReady);
    }

    private static void decline(MysticalAugments.Family family, Throwable failure) {
        if (!DISABLED.add(family)) { return; }
        if (failure == null) { Constants.LOG.warn("Mystical Agriculture mixed augment disabled: {}", family); }
        else { Constants.LOG.warn("Mystical Agriculture mixed augment disabled: " + family, failure); }
    }

    private static Binding bindings() {
        if (attempted) { return binding; }
        synchronized (MysticalAugmentsCompatibility.class) {
            if (attempted) { return binding; }
            try {
                Class<?> augment = Class.forName(PREFIX + "api.tinkering.Augment");
                Class<?> tinkerable = Class.forName(PREFIX + "api.tinkering.ITinkerable");
                Class<?> entry = Class.forName(PREFIX + "api.components.AugmentComponent");
                Object registry = Class.forName(PREFIX + "api.MysticalAgricultureAPI").getMethod("getAugmentRegistry").invoke(null);
                Method lookup = registry.getClass().getMethod("getAugmentById", Identifier.class);
                var augments = new java.util.HashMap<Identifier, Object>();
                var ready = java.util.EnumSet.noneOf(MysticalAugments.Family.class);
                boolean equipment = proof(Class.forName(PREFIX + "api.util.AugmentUtils"), EQUIPMENT_PROOF);
                for (var spec : MysticalAugments.CATALOG.values()) {
                    Object value = lookup.invoke(registry, spec.id());
                    if (value == null || !value.getClass().getName().equals(spec.family().nativeClass())) { continue; }
                    augments.put(spec.id(), value);
                    if (spec.family().route == MysticalAugments.Route.ARMOR
                            && proof(value.getClass(), TICK_PROOF)) { ready.add(spec.family()); }
                }
                DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
                binding = new Binding(Class.forName(PREFIX + "item.armor.EssenceBootsItem"), entry, component,
                        tinkerable.getMethod("getAugmentSlots"), tinkerable.getMethod("getTinkerableTier"),
                        tinkerable.getMethod("canApplyAugment", augment), entry.getMethod("slot"), entry.getMethod("id"),
                        augment.getMethod("onArmorTick", ItemStack.class, ServerLevel.class, Player.class),
                        Map.copyOf(augments), Set.copyOf(ready), equipment);
                for (var family : MysticalAugments.Family.values()) {
                    if (!available(family, binding)) { decline(family, null); }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                Constants.LOG.warn("Mystical Agriculture mixed augment API unavailable", failure);
            } finally { attempted = true; }
            return binding;
        }
    }

    private static boolean proof(Class<?> owner, String name) {
        try {
            Method marker = owner.getDeclaredMethod(name); marker.setAccessible(true);
            return Boolean.TRUE.equals(marker.invoke(null));
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError incompatible) { return false; }
    }

    private record Binding(Class<?> bootType, Class<?> entryType, DataComponentType<?> component,
                           Method slots, Method tier, Method canApply, Method slot, Method id, Method armorTick,
                           Map<Identifier, Object> augments, Set<MysticalAugments.Family> tickReady, boolean equipmentReady) {}
}
