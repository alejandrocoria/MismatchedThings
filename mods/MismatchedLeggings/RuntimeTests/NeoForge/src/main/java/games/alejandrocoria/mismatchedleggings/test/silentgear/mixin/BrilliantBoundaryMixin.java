package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.item.gear.GearArmorItem", priority = 800, remap = false)
abstract class BrilliantBoundaryMixin {}
