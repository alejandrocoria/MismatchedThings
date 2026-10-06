package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Plugin changes only the production-owned getter; the native consumer and HEAD owner stay valid. */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", priority = 500, remap = false)
abstract class TidePublicationChangeMixin {}
