package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Adds only worn LEGS recognition; SG still owns the height gate, repeated push and potions. */
public final class SilentGearVoidWard {
    private static volatile SilentGearEffectSources sources;
    private static volatile boolean disabled;

    private SilentGearVoidWard() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearVoidWard::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var type = SilentGearCompatibility.nativeClass("event.GearEvents");
            if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                    .findStatic(type, "mismatchedleggings$sgVoidReady", MethodType.methodType(boolean.class)).invoke()) {
                throw new IllegalStateException("Final Void Ward query proof unavailable");
            }
            sources = new SilentGearEffectSources();
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    public static boolean recognize(boolean original, PlayerTickEvent.Pre event, boolean proof) {
        var binding = sources;
        if (original || !proof || disabled || binding == null || event.getEntity().level().isClientSide()) {
            return original;
        }

        var root = event.getEntity().getItemBySlot(EquipmentSlot.LEGS);
        if (MixedLeggingsStructure.validData(root).isEmpty()) {
            return original;
        }
        try {
            return binding.traits(root).stream().anyMatch(trait -> trait.id().toString().equals("silentgear:void_ward"));
        } catch (Throwable failure) {
            disable(failure);
            return original;
        }
    }

    private static synchronized void disable(Throwable failure) {
        sources = null;
        if (disabled) {
            return;
        }
        disabled = true;
        LeggingsContent.LOG.warn("Silent Gear mixed leggings void ward disabled: recognition contract unavailable", failure);
    }
}
