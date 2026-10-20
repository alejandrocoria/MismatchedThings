package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Two independent read-only features: no full trait dispatch, equipment swaps or new saved state. */
public final class SilentGearRecognition {
    private static volatile SilentGearWearerApi.Carriers carriers;
    private static volatile boolean moonReady, brilliantDisabled, moonDisabled;

    private SilentGearRecognition() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) { bus.addListener(SilentGearRecognition::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            requireProof("item.gear.GearArmorItem", "mismatchedboots$sgBrilliantReady");
            carriers = new SilentGearWearerApi.Carriers();
        } catch (Throwable failure) { disable(true, failure); }
        try {
            requireProof("event.GearEvents", "mismatchedboots$sgMoonReady");
            moonReady = true;
        } catch (Throwable failure) { disable(false, failure); }
    }

    private static void requireProof(String owner, String getter) throws Throwable {
        var type = Class.forName("net.silentchaos512.gear." + owner);
        if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, getter, MethodType.methodType(boolean.class)).invoke()) {
            throw new IllegalStateException("Final recognition proof unavailable");
        }
    }

    public static boolean brilliant(ItemStack root, LivingEntity actor, boolean original) {
        if (original || brilliantDisabled || carriers == null) { return original; }
        try {
            if (SilentGearCompatibility.effectiveTraits(root).stream().noneMatch(trait -> trait.id().toString().equals("silentgear:brilliant"))) { return false; }
            ItemStack carrier = carriers.carrier(root), before = carrier.copy();
            if (carrier.isEmpty()) { return false; }
            boolean result = carrier.getItem().makesPiglinsNeutral(carrier, actor);
            if (!ItemStack.matches(before, carrier)) { throw new IllegalStateException("Native Brilliant query changed its carrier"); }
            return result;
        } catch (Throwable failure) { disable(true, failure); return false; }
    }

    /** The original armor/optional-Curios result wins when it is higher; native SG still does the fall math. */
    public static int moonwalker(int original, LivingFallEvent event, boolean proof) {
        if (!proof || !moonReady || moonDisabled || event.isCanceled() || !(event.getEntity() instanceof Player player)) { return original; }
        try {
            int level = SilentGearCompatibility.effectiveTraits(player.getItemBySlot(EquipmentSlot.FEET)).stream()
                    .filter(trait -> trait.id().toString().equals("silentgear:moonwalker")).mapToInt(SilentGearCompatibility.Trait::level).max().orElse(0);
            return Math.max(original, level);
        } catch (Throwable failure) { disable(false, failure); return original; }
    }

    private static synchronized void disable(boolean brilliant, Throwable failure) {
        if (brilliant ? brilliantDisabled : moonDisabled) { return; }
        if (brilliant) { brilliantDisabled = true; } else { moonDisabled = true; }
        Constants.LOG.warn("Silent Gear mixed {} disabled: recognition contract unavailable", brilliant ? "brilliant" : "moonwalker", failure);
    }
}
