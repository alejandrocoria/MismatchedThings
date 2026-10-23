package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorMarker;
import games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MixedBootsTooltipFormatter.class)
abstract class SourceSeparatorMixin {
    @ModifyArg(method = "appendSourceSections(Lgames/alejandrocoria/mismatchedboots/data/MixedBootsData;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;Ljava/util/function/BiFunction;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"), index = 0)
    private static Object separator(Object original) {
        return SeparatorMarker.enabled() && SeparatorMarker.inserting ? new SeparatorMarker() : original;
    }
}
