package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.block.fluid.AlkahestFluid", remap = false)
abstract class AetherAlkahestFluidTestMixin {}
