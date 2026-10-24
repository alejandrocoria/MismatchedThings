package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.block.fluid.AlkahestFluid", remap = false)
abstract class AetherAlkahestFluidTargetMixin {}
