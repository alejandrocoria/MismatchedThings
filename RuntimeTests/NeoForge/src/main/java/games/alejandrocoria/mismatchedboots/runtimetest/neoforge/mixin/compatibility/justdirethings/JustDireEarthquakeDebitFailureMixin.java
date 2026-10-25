package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeServerGuardPlugin;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility$LiveHandler", remap = false)
abstract class JustDireEarthquakeDebitFailureMixin {
    @ModifyReturnValue(method = "extract", at = @At("RETURN"))
    private int fail(int extracted) {
        if (extracted <= 0 || JustDireEarthquakeServerGuardPlugin.fault == 0
                || ++JustDireEarthquakeServerGuardPlugin.debits != JustDireEarthquakeServerGuardPlugin.failAt) {
            return extracted;
        }
        int fault = JustDireEarthquakeServerGuardPlugin.fault;
        var player = JustDireEarthquakeServerGuardPlugin.player;
        if (fault == 1) {
            return extracted - 1;
        }
        if (fault == 2) {
            player.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
        }
        if (fault == 3) {
            JustDireEarthquakeServerGuardPlugin.changeToggle();
        }
        return extracted;
    }
}
