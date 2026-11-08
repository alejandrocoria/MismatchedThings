package games.alejandrocoria.mismatchedleggings.mixin.compatibility.mysticalagriculture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {
        "com.blakebr0.mysticalagriculture.augment.AbsorptionAugment",
        "com.blakebr0.mysticalagriculture.augment.FireResistanceAugment",
        "com.blakebr0.mysticalagriculture.augment.LuckAugment",
        "com.blakebr0.mysticalagriculture.augment.MiningFatigueResistanceAugment",
        "com.blakebr0.mysticalagriculture.augment.PoisonResistanceAugment",
        "com.blakebr0.mysticalagriculture.augment.SlownessResistanceAugment",
        "com.blakebr0.mysticalagriculture.augment.WitherResistanceAugment"
}, remap = false)
abstract class MysticalArmorTickProofMixin {
    @Unique
    private static boolean mismatchedleggings$maArmorTickReady() {
        return false;
    }
}
