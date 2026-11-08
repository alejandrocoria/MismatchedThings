package games.alejandrocoria.mismatchedleggings.test.undergarden.mixin;

import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quek.undergarden.registry.UGDataComponents;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter", remap = false)
abstract class BootsSourceCandidateMixin {
    @Inject(method = "hideGameplayComponents", at = @At("RETURN"), cancellable = true)
    private static void spike$hide(TooltipDisplay display, CallbackInfoReturnable<TooltipDisplay> callback) {
        callback.setReturnValue(callback.getReturnValue().withHidden(UGDataComponents.ROGDORIUM_INFUSION.get(), true));
    }
}
