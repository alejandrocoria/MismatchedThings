package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/** Native other-slot Bounce may charge any FEET item: this barrier does not require a mixed SG trait. */
public final class SilentGearDefense {
    private static volatile boolean fallReady, fallDisabled;
    private SilentGearDefense() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) { bus.addListener(SilentGearDefense::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            SilentGearDefenseApi.require("event.GearEvents", "mismatchedboots$sgSafeFallReady");
            SilentGearDefenseApi.requireSchedule();
            fallReady = true;
        } catch (Throwable failure) { disable(failure); }
    }

    public static boolean valid(ItemStack root) {
        var equipment = root.get(DataComponents.EQUIPPABLE);
        return root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && ModContent.getMixedBootsData(root).isPresent() && equipment != null && equipment.slot() == EquipmentSlot.FEET;
    }

    /** A adds no mixed Bounce level. Refused wear must also deny a native peer before any branch effects. */
    public static boolean allowFall(LivingFallEvent event, boolean proof) {
        ItemStack feet = event.getEntity().getItemBySlot(EquipmentSlot.FEET);
        if (!(feet.getItem() instanceof MixedBootsItem)) { return true; }
        return proof && fallReady && !fallDisabled && valid(feet) && !event.isCanceled();
    }

    /** Only non-mixed targets may reach native gear wear, including when mixed admission is refused. */
    public static boolean allowNativeFallWear(ItemStack root, int amount, LivingEntity actor, EquipmentSlot slot, boolean proof) {
        if (!(root.getItem() instanceof MixedBootsItem)) { return true; }
        if (!proof || !fallReady || fallDisabled || !valid(root) || slot != EquipmentSlot.FEET
                || actor.level().isClientSide() || actor.getItemBySlot(slot) != root || amount <= 0) { return false; }
        // Keep normal SG durability/JDT/vanilla wear routing. No catch/retry can safely replay this payment.
        root.hurtAndBreak(amount, actor, slot);
        return false;
    }

    private static synchronized void disable(Throwable failure) {
        if (!fallDisabled) {
            fallDisabled = true;
            Constants.LOG.warn("Silent Gear mixed safe fall disabled: native fall/wear contract unavailable", failure);
        }
    }
}
