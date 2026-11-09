package games.alejandrocoria.mismatchedleggings.test.iceandfire.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", priority = 800, remap = false)
abstract class DamageControlMixin {}
