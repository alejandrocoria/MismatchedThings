package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireGroundStompServerGuardPlugin;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility$LiveHandler", remap = false)
abstract class JustDireGroundStompDebitFailureMixin {
    @ModifyReturnValue(method = "extract", at = @At("RETURN"))
    private int fail(int extracted) {
        if (extracted > 0) { JustDireGroundStompServerGuardPlugin.afterDebit(); }
        int fault = JustDireGroundStompServerGuardPlugin.fault;
        if (extracted > 0 && fault > 0 && fault < 4) {
            JustDireGroundStompServerGuardPlugin.extracted = extracted;
            if (fault == 1) { return extracted - 1; }
            var player = JustDireGroundStompServerGuardPlugin.player;
            if (fault == 2) { player.setItemSlot(EquipmentSlot.FEET, JustDireGroundStompServerGuardPlugin.replacement); }
            if (fault == 3) {
                JustDireGroundStompServerGuardPlugin.changeToggle();
            }
        }
        return extracted;
    }
}
