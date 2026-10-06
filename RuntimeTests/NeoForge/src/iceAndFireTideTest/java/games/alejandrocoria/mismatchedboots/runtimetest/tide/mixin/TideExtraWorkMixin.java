package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.tide.TideCarrierProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", priority = 500, remap = false)
abstract class TideExtraWorkMixin {
    @Inject(method = "inventoryTick", at = @At("RETURN"))
    private void tideTest$opaqueWork(CallbackInfo ci) { TideCarrierProbe.extraWork(); }
}
