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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Independent read-only recognition: no full trait dispatch, equipment swaps or new saved state. */
public final class SilentGearRecognition {
    public static boolean snowWalker(ItemStack root) {
        return SilentGearCompatibility.effectiveTraits(root).stream()
                                      .anyMatch(trait -> trait.id().toString().equals("silentgear:snow_walker"));
    }

    private static volatile SilentGearWearerApi.Carriers carriers;
    private static volatile boolean moonReady;
    private static volatile boolean voidReady;
    private static volatile boolean brilliantDisabled;
    private static volatile boolean moonDisabled;
    private static volatile boolean voidDisabled;

    private SilentGearRecognition() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearRecognition::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            requireProof("item.gear.GearArmorItem", "mismatchedboots$sgBrilliantReady");
            carriers = new SilentGearWearerApi.Carriers();
        } catch (Throwable failure) {
            disable(true, failure);
        }

        try {
            requireProof("event.GearEvents", "mismatchedboots$sgMoonReady");
            moonReady = true;
        } catch (Throwable failure) {
            disable(false, failure);
        }

        try {
            requireProof("event.GearEvents", "mismatchedboots$sgVoidReady");
            voidReady = true;
        } catch (Throwable failure) {
            disableVoid(failure);
        }
    }

    private static void requireProof(String owner, String getter) throws Throwable {
        var type = Class.forName("net.silentchaos512.gear." + owner);
        if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                                    .findStatic(type, getter, MethodType.methodType(boolean.class))
                                    .invoke()) {
            throw new IllegalStateException("Final recognition proof unavailable");
        }
    }

    public static boolean brilliant(ItemStack root, LivingEntity actor, boolean original) {
        if (original || brilliantDisabled || carriers == null) {
            return original;
        }

        try {
            if (SilentGearCompatibility.effectiveTraits(root).stream().noneMatch(trait -> trait.id().toString().equals("silentgear:brilliant"))) {
                return false;
            }
            ItemStack carrier = carriers.carrier(root);
            ItemStack before = carrier.copy();
            if (carrier.isEmpty()) {
                return false;
            }
            boolean result = carrier.getItem().makesPiglinsNeutral(carrier, actor);
            if (!ItemStack.matches(before, carrier)) {
                throw new IllegalStateException("Native Brilliant query changed its carrier");
            }
            return result;
        } catch (Throwable failure) {
            disable(true, failure);
            return false;
        }
    }

    /** The original armor/optional-Curios result wins when it is higher; native SG still does the fall math. */
    public static int moonwalker(int original, LivingFallEvent event, boolean proof) {
        if (!proof || !moonReady || moonDisabled || event.isCanceled() || !(event.getEntity() instanceof Player player)) {
            return original;
        }

        try {
            int level = SilentGearCompatibility.effectiveTraits(player.getItemBySlot(EquipmentSlot.FEET))
                                               .stream()
                                               .filter(trait -> trait.id().toString().equals("silentgear:moonwalker"))
                                               .mapToInt(SilentGearCompatibility.Trait::level)
                                               .max()
                                               .orElse(0);
            return Math.max(original, level);
        } catch (Throwable failure) {
            disable(false, failure);
            return original;
        }
    }

    /** SG owns the height gate, repeated rescue, motion and potions; only its armor-query result is extended. */
    public static boolean voidWard(boolean original, PlayerTickEvent.Pre event, boolean proof) {
        if (original || !proof || !voidReady || voidDisabled || event.getEntity().level().isClientSide()) {
            return original;
        }
        try {
            return SilentGearCompatibility.effectiveTraits(event.getEntity().getItemBySlot(EquipmentSlot.FEET)).stream()
                                          .anyMatch(trait -> trait.id().toString().equals("silentgear:void_ward"));
        } catch (Throwable failure) {
            disableVoid(failure);
            return original;
        }
    }

    private static synchronized void disableVoid(Throwable failure) {
        if (voidDisabled) {
            return;
        }
        voidDisabled = true;
        Constants.LOG.warn("Silent Gear mixed void ward disabled: recognition contract unavailable", failure);
    }

    private static synchronized void disable(boolean brilliant, Throwable failure) {
        if (brilliant ? brilliantDisabled : moonDisabled) {
            return;
        }
        if (brilliant) {
            brilliantDisabled = true;
        } else {
            moonDisabled = true;
        }
        Constants.LOG.warn("Silent Gear mixed {} disabled: recognition contract unavailable", brilliant ? "brilliant" : "moonwalker", failure);
    }
}
