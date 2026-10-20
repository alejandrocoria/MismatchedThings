package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"net.silentchaos512.gear.item.gear.GearArmorItem", "net.silentchaos512.gear.event.GearEvents"}, priority = 800, remap = false)
abstract class SilentGearRecognitionFaultMixin {}
