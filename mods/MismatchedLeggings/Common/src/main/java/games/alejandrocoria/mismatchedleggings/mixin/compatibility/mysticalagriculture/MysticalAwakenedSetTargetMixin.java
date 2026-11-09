package games.alejandrocoria.mismatchedleggings.mixin.compatibility.mysticalagriculture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Enrolls the optional native consumer for a slot-local final-export edit. */
@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.item.armor.EssenceChestplateItem", remap = false)
abstract class MysticalAwakenedSetTargetMixin {}

