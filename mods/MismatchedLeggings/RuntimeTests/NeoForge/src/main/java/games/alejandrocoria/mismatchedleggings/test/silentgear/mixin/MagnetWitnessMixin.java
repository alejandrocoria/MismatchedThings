package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect", "net.silentchaos512.gear.gear.util.MagnetPullTracker"}, remap = false)
abstract class MagnetWitnessMixin {}
