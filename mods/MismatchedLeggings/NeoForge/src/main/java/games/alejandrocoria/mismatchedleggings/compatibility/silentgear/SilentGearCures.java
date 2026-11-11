package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;

/** Selected actor-only cancellations, not native gear ticking or permanent potion immunity. */
public final class SilentGearCures {
    private static volatile SilentGearCureApi api;

    private enum Cure {
        POISON("poison"), WITHER("wither");

        final String target;
        volatile boolean disabled;

        Cure(String target) {
            this.target = target;
        }

        synchronized void disable(Throwable failure) {
            if (disabled) {
                return;
            }
            disabled = true;
            LeggingsContent.LOG.warn("Silent Gear mixed leggings cure {} disabled: cancellation contract unavailable", target, failure);
        }
    }

    private SilentGearCures() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearCures::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            api = new SilentGearCureApi();
        } catch (Throwable failure) {
            for (var cure : Cure.values()) {
                cure.disable(failure);
            }
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var binding = api;
        if (binding == null || Cure.POISON.disabled && Cure.WITHER.disabled || level.isClientSide()
                || !(entity instanceof Player player) || player.level() != level
                || slot != EquipmentSlot.LEGS && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND
                || MixedLeggingsStructure.validData(root).isEmpty()) {
            return;
        }

        List<SilentGearEffectSources.Trait> traits;
        try {
            traits = binding.traits(root);
        } catch (Throwable failure) {
            // An unreadable shared source view cannot safely select either cure; other adapters remain independent.
            for (var cure : Cure.values()) {
                cure.disable(failure);
            }
            return;
        }
        for (var trait : traits) {
            Cure cure = switch (trait.id().toString()) {
                case "silentgear:cure_poison" -> Cure.POISON;
                case "silentgear:cure_wither" -> Cure.WITHER;
                default -> null;
            };
            if (cure == null || cure.disabled) {
                continue;
            }

            try {
                if (trait.level() != 1) {
                    throw new IllegalStateException("Changed cure level");
                }
                Object effect = binding.admitted(trait.nativeInstance(), cure.target);
                ItemStack copy = root.copy();
                ItemStack before = copy.copy();
                binding.tick(effect, trait.nativeInstance(), copy, level, player);
                if (!ItemStack.matches(copy, before)) {
                    throw new IllegalStateException("Cancellation changed its disposable stack");
                }
            } catch (Throwable failure) {
                // Removal may already have happened. Do not roll it back, replay, or skip the other cure.
                cure.disable(failure);
            }
        }
    }
}
