package games.alejandrocoria.mismatchedleggings.test.mystical;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Happy-path research adapter only: no production authorization/guard/catalog is claimed. */
public final class MysticalCandidate {
    private static final String PREFIX = "com.blakebr0.mysticalagriculture.";
    private static final Set<String> FAMILIES = Set.of("absorption", "fire_resistance", "health_boost", "luck",
            "mining_fatigue_resistance", "poison_resistance", "slowness_resistance", "speed", "step_assist", "wither_resistance");
    private static final Set<String> ITEMS = Set.of("inferium_leggings", "prudentium_leggings", "tertium_leggings",
            "imperium_leggings", "supremium_leggings", "awakened_supremium_leggings");
    public static int deliveries;

    private MysticalCandidate() {}

    public static Object invoke(Object receiver, String name, Class<?>[] types, Object... args) {
        try {
            return (receiver instanceof Class<?> type ? type : receiver.getClass()).getMethod(name, types)
                    .invoke(receiver instanceof Class<?> ? null : receiver, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    public static Class<?> type(String name) {
        try { return Class.forName(PREFIX + name); } catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
    }

    public static Object augment(String name) {
        try { return type("lib.ModAugments").getField(name).get(null); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static ItemStack item(String path, String... entries) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mysticalagriculture:" + path)));
        require(!stack.isEmpty(), "Missing native " + path);
        for (int slot = 0; slot < entries.length; slot++) {
            Object augment = augment(entries[slot]);
            require(Boolean.TRUE.equals(invoke(stack.getItem(), "canApplyAugment", new Class<?>[]{type("api.tinkering.Augment")}, augment)), "Bad native source installation");
            invoke(type("api.util.AugmentUtils"), "addAugment", new Class<?>[]{ItemStack.class, type("api.tinkering.Augment"), int.class}, stack, augment, slot);
        }
        return stack;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> nativeAugments(ItemStack stack) {
        return (List<Object>) invoke(type("api.util.AugmentUtils"), "getAugments", new Class<?>[]{ItemStack.class}, stack);
    }

    public static String id(Object augment) {
        return invoke(augment, "getId", new Class<?>[0]).toString();
    }

    private static int rank(Object augment) {
        String id = id(augment);
        return id.endsWith("_v") ? 5 : id.endsWith("_iv") ? 4 : id.endsWith("_iii") ? 3 : id.endsWith("_ii") ? 2 : 1;
    }

    public static List<Object> selected(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) { return List.of(); }
        var result = new LinkedHashMap<String, Object>();
        for (var source : List.of(data.left().source(), data.right().source())) {
            var itemId = BuiltInRegistries.ITEM.getKey(source.getItem());
            if (!itemId.getNamespace().equals("mysticalagriculture") || !ITEMS.contains(itemId.getPath())) { continue; }
            for (var augment : nativeAugments(source)) {
                String family = Identifier.parse(id(augment)).getPath().replaceFirst("_(i|ii|iii|iv|v)$", "");
                if (FAMILIES.contains(family)) {
                    result.merge(family, augment, (a, b) -> rank(a) >= rank(b) ? a : b);
                }
            }
        }
        return List.copyOf(result.values());
    }

    public static List<Object> equipment(Player player, ItemStack stack, List<Object> ordinary) {
        if (player.getItemBySlot(EquipmentSlot.LEGS) != stack) { return ordinary; }
        var result = new ArrayList<>(ordinary);
        result.addAll(selected(stack));
        return result;
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.LEGS || !(entity instanceof Player player) || player.getItemBySlot(slot) != root) { return; }
        for (var augment : selected(root)) {
            invoke(augment, "onArmorTick", new Class<?>[]{ItemStack.class, ServerLevel.class, Player.class}, root, level, player);
        }
    }

    public static void health(Object augment, Player player, Object cache) {
        if (!id(augment).contains(":health_boost_") || !selected(player.getItemBySlot(EquipmentSlot.LEGS)).contains(augment)) { return; }
        if (!Boolean.TRUE.equals(invoke(cache, "isCached", new Class<?>[]{type("api.tinkering.Augment"), Player.class}, augment, player))) { return; }
        Runnable cleanup = () -> {
            player.getAttribute(Attributes.MAX_HEALTH).removeModifier(Identifier.parse("mysticalagriculture:health_boost_augment"));
            if (player.getHealth() > player.getMaxHealth()) { player.setHealth(player.getMaxHealth()); }
        };
        invoke(cache, "add", new Class<?>[]{type("api.tinkering.Augment"), Player.class, Runnable.class}, augment, player, cleanup);
    }

    public record Capture(ItemStack root, Item item, ServerPlayer owner, int slot, DataComponentMap live, List<Item> purchases) {}

    public static Capture capture(ItemStack root, int damage, net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || root.getCount() != 1 || !root.isDamageableItem() || damage < root.getMaxDamage()) { return null; }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null || !ITEMS.contains(BuiltInRegistries.ITEM.getKey(data.left().source().getItem()).getPath())) { return null; }
        var purchases = nativeAugments(data.left().source()).stream().map(a -> (Item) invoke(a, "getItem", new Class<?>[0])).toList();
        if (purchases.isEmpty()) { return null; }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == root) { return new Capture(root, root.getItem(), player, slot, root.getComponents(), purchases); }
        }
        return null;
    }

    public static void complete(Capture capture) {
        if (capture == null || capture.root.getCount() != 0 || capture.owner.getInventory().getItem(capture.slot) != capture.root) { return; }
        var shadow = new ItemStack(capture.item);
        List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
        shadow.applyComponents(capture.live);
        var data = MixedLeggingsStructure.validData(shadow).orElseThrow();
        require(nativeAugments(data.left().source()).stream().map(a -> (Item) invoke(a, "getItem", new Class<?>[0])).toList().equals(capture.purchases), "Changed refund entitlement");
        for (Item item : capture.purchases) { capture.owner.getInventory().placeItemBackInInventory(new ItemStack(item)); deliveries++; }
    }

    public static ItemStack mixed(String field, boolean inverse) {
        var first = item("awakened_supremium_leggings", field);
        var second = item("inferium_leggings", "LUCK_I");
        var pair = MixedLeggingsFactory.createPair(first, second);
        return inverse ? pair.inverse() : pair.primary();
    }
}
