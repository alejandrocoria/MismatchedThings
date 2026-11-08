package games.alejandrocoria.mismatchedleggings.test.mysticaleffects.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
@Pseudo
@Mixin(targets = {"com.blakebr0.mysticalagriculture.api.util.AugmentUtils",
        "com.blakebr0.mysticalagriculture.augment.LuckAugment",
        "com.blakebr0.mysticalagriculture.augment.HealthBoostAugment"}, remap = false)
abstract class GuardTargets {
    @Unique private static void leggings$effectsFixture() {}
}
