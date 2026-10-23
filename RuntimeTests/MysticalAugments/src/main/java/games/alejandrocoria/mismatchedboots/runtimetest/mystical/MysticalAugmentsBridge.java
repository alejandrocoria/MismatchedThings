package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Disposable any-source/strongest-family candidate, not a production composition policy. */
public final class MysticalAugmentsBridge {
    private static final Set<String> FAMILIES = Set.of("AbsorptionAugment", "FireResistanceAugment",
            "HealthBoostAugment", "JumpBoostAugment", "LuckAugment", "MiningFatigueResistanceAugment",
            "NoFallDamageAugment", "PoisonResistanceAugment", "SlowFallingAugment", "StepAssistAugment",
            "WitherResistanceAugment");
    public static boolean active = true;
    public static boolean refundsActive;

    private MysticalAugmentsBridge() {}

    public static boolean enabled() {
        return !System.getProperty("mismatchedboots.test.mysticalAugments", "").isEmpty();
    }

    public static List<Object> resolve(ItemStack root) {
        var equippable = root.get(DataComponents.EQUIPPABLE);
        if (!active || !ModContent.isMixedBoots(root) || root.getCount() != 1
                || equippable == null || equippable.slot() != EquipmentSlot.FEET) { return List.of(); }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return List.of(); }
        var selected = new LinkedHashMap<String, Object>();
        for (ItemStack source : List.of(data.left().source(), data.right().source())) {
            if (!source.getItem().getClass().getName().equals(
                    "com.blakebr0.mysticalagriculture.item.armor.EssenceBootsItem")) { continue; }
            for (Object augment : nativeAugments(source)) {
                String family = augment.getClass().getSimpleName();
                if (!FAMILIES.contains(family)) { continue; }
                Object previous = selected.get(family);
                if (previous == null || tier(augment) > tier(previous)) { selected.put(family, augment); }
            }
        }
        return new ArrayList<>(selected.values());
    }

    public static List<Object> armor(Player player, List<Object> nativeResult) {
        var result = new ArrayList<>(nativeResult);
        result.addAll(resolve(player.getItemBySlot(EquipmentSlot.FEET)));
        return result;
    }

    /** Refund ownership is per original input, not the deduplicated/strongest effective list. */
    public static ItemStack refundSource(ItemStack root) {
        if (!refundsActive || !ModContent.isMixedBoots(root) || root.getCount() != 1) { return null; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return null; }
        ItemStack owner = data.left().source();
        return owner.getItem().getClass().getName().equals(
                "com.blakebr0.mysticalagriculture.item.armor.EssenceBootsItem") ? owner : null;
    }

    public static void tick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.FEET || !(entity instanceof Player player)
                || player.getItemBySlot(slot) != stack) { return; }
        for (Object augment : resolve(stack)) {
            invoke(augment, "onArmorTick", new Class<?>[]{ItemStack.class, ServerLevel.class, Player.class}, stack, level, player);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> nativeAugments(ItemStack stack) {
        return (List<Object>) call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "getAugments",
                new Class<?>[]{ItemStack.class}, stack);
    }

    public static int tier(Object augment) {
        return ((Number) invoke(augment, "getTier", new Class<?>[]{})).intValue();
    }

    public static Object call(String type, String method, Class<?>[] types, Object... args) {
        try { return Class.forName(type).getMethod(method, types).invoke(null, args); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(type + "." + method, exception); }
    }

    public static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(name, exception); }
    }
}
