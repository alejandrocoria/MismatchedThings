package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.undergarden;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "quek.undergarden.block.GooLayerBlock", remap = false)
abstract class UndergardenTestTargetMixin {}
