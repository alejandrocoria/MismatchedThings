package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.runtimetest.tide.TideCarrierProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", priority = 2000, remap = false)
abstract class TideChainHighMixin {
    @ModifyExpressionValue(method = "inventoryTick", at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.tide.TideSlotInjectionPoint", args = "slot=FEET"))
    private boolean tideTest$highFeet(boolean original) { return TideCarrierProbe.chain(original); }

    @ModifyExpressionValue(method = "inventoryTick", at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.tide.TideSlotInjectionPoint", args = "slot=HEAD"))
    private boolean tideTest$highHead(boolean original) { return TideCarrierProbe.chain(original); }
}
