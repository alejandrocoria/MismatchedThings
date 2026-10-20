package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRecognitionTest;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Controlled original-query contribution, not a claim of actual Curios installation/acceptance. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", priority = 900, remap = false)
abstract class SilentGearMoonQueryPeerMixin {
    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMoonwalkerInjectionPoint"), require = 1)
    private static int sgtest$optionalQuery(int original, LivingFallEvent event) {
        return Math.max(original, SilentGearRecognitionTest.queryContribution);
    }
}
