package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearMoonwalkerMixin {
    @Unique
    private static boolean mismatchedboots$sgMoonReady() {
        return false;
    }

    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMoonwalkerInjectionPoint"), require = 0, expect = 0, allow = 1)
    private static int mismatchedboots$sgMoonLevel(int original, LivingFallEvent event) {
        return SilentGearRecognition.moonwalker(original, event, mismatchedboots$sgMoonReady());
    }
}
