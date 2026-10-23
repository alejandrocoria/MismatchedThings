package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorLine;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorMarker;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric's text conversion seam; NeoForge normally converts the marker during gathering. */
@Mixin(ClientTooltipComponent.class)
interface SeparatorFactoryMixin {
    @Inject(method = "create(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
    private static void separator(FormattedCharSequence text, CallbackInfoReturnable<ClientTooltipComponent> result) {
        if (SeparatorMarker.enabled() && text instanceof SeparatorMarker.Sequence) { result.setReturnValue(new SeparatorLine()); }
    }
}
