package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/** Only fixture state; mutations run after the REAL FE handler has staged extraction. */
public final class JustDireArmorWearFaults {
    public static String fault = "";
    public static ServerPlayer player;
    public static ItemStack root, replacement;
    public static int extracted, before, after, surrounding;
    public static final class CommittedFailure extends RuntimeException {}
    public static void before() { before++; }
    public static void after() { after++; }
    public static void surrounding() { surrounding++; }
    public static ItemStack entry(ArmorHurtEvent.ArmorEntry entry) { return entry.armorItemStack; }
    public static LivingEntity entity(LivingEntity entity) { return entity; }

    public static int extracted(int receipt) {
        if (receipt <= 0 || fault.isEmpty()) { return receipt; }
        if (fault.equals("committed")) { extracted = receipt; return receipt; }
        String current = fault; fault = ""; extracted = receipt;
        switch (current) {
            case "short" -> { return receipt - 1; }
            case "reenter" -> CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, player);
            case "replace" -> player.setItemSlot(EquipmentSlot.FEET, replacement);
            case "wallet" -> root.set(JustDireResourceTestSupport.energyComponent(), 77);
            case "component" -> root.set(DataComponents.CUSTOM_NAME, Component.literal("independent edit"));
            case "staged" -> { /* handled by the mixin against its real access */ }
            default -> throw new AssertionError("Unknown extraction fault " + current);
        }
        return receipt;
    }
}
