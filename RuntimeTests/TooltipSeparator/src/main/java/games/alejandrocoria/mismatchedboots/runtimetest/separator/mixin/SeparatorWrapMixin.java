package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorLine;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorMarker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Empty marker must survive text wrapping; ordinary text always follows the native implementation. */
@Mixin(Font.class)
abstract class SeparatorWrapMixin {
    @Inject(method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void separator(FormattedText text, int width, CallbackInfoReturnable<List<FormattedCharSequence>> result) {
        if (SeparatorMarker.enabled() && text instanceof SeparatorMarker) {
            SeparatorLine.preservedSplits++;
            result.setReturnValue(List.of(SeparatorMarker.Sequence.INSTANCE));
        }
    }
}
