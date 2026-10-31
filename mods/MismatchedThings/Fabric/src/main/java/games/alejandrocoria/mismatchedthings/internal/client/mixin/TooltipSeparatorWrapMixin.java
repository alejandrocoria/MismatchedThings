package games.alejandrocoria.mismatchedthings.internal.client.mixin;

import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Font.class)
abstract class TooltipSeparatorWrapMixin {
    @Inject(method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void mismatchedthings$separator(FormattedText text, int width, CallbackInfoReturnable<List<FormattedCharSequence>> result) {
        // Normal empty-text wrapping erases identity; preserve only our graphical section boundary.
        if (text instanceof TooltipSeparator) {
            result.setReturnValue(List.of(TooltipSeparator.Sequence.INSTANCE));
        }
    }
}
