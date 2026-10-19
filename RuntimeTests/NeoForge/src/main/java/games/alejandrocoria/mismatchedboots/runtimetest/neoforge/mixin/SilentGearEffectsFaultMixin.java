package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"net.silentchaos512.gear.gear.trait.effect.SelfRepairTraitEffect",
        "net.silentchaos512.gear.gear.trait.effect.DurabilityTraitEffect"}, priority = 800, remap = false)
abstract class SilentGearEffectsFaultMixin {}
