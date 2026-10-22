package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipTestControl;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.AetherIIEventListeners", remap = false)
abstract class AetherTooltipPeerMixin {
    @Inject(method = "onAddAttributeTooltips", at = @At("TAIL"))
    private static void peer(AddAttributeTooltipsEvent event, CallbackInfo ci) {
        event.addTooltipLines(AetherTooltipTestControl.PEER);
    }
}
