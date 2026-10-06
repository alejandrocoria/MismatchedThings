package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Priority anchor: the plugin changes the real method during this mixin's preApply. */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", priority = 500, remap = false)
abstract class TidePredicateChangeMixin {}
