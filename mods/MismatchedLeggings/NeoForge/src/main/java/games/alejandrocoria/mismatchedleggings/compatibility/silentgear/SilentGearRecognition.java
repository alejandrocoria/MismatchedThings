package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Item-local Brilliant query, independent from repair, live state and Wielder readiness. */
public final class SilentGearRecognition {
    private static volatile SilentGearWearerApi.Carriers carriers;
    private static volatile boolean disabled;

    private SilentGearRecognition() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener((RegisterCapabilitiesEvent event) -> {
                try {
                    var type = SilentGearCompatibility.nativeClass("item.gear.GearArmorItem");
                    if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, "mismatchedleggings$sgBrilliantReady",
                            MethodType.methodType(boolean.class)).invoke()) {
                        throw new IllegalStateException("Final Brilliant callback proof unavailable");
                    }
                    carriers = new SilentGearWearerApi.Carriers();
                } catch (Throwable failure) {
                    disable(failure);
                }
            });
        }
    }

    public static boolean brilliant(ItemStack root, LivingEntity actor, boolean original) {
        var binding = carriers;
        if (original || disabled || binding == null) { return original; }
        try {
            var carrier = binding.carrier(root);
            if (carrier.isEmpty()) { return false; }
            var before = carrier.copy();
            boolean result = carrier.getItem().makesPiglinsNeutral(carrier, actor);
            if (!ItemStack.matches(before, carrier)) {
                throw new IllegalStateException("Native Brilliant query changed its carrier");
            }
            return result;
        } catch (Throwable failure) {
            disable(failure);
            return original;
        }
    }

    private static synchronized void disable(Throwable failure) {
        if (disabled) { return; }
        disabled = true;
        LeggingsContent.LOG.warn("Silent Gear mixed leggings brilliant disabled: recognition contract unavailable", failure);
    }
}
