package games.alejandrocoria.mismatchedthings.internal.client.mixin;

import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import games.alejandrocoria.mismatchedthings.internal.client.tooltip.TooltipSeparatorComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTooltipComponent.class)
interface TooltipSeparatorFactoryMixin {
    @Inject(method = "create(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
    private static void mismatchedthings$separator(FormattedCharSequence text, CallbackInfoReturnable<ClientTooltipComponent> result) {
        if (text instanceof TooltipSeparator.Sequence) {
            result.setReturnValue(TooltipSeparatorComponent.INSTANCE);
        }
    }
}
