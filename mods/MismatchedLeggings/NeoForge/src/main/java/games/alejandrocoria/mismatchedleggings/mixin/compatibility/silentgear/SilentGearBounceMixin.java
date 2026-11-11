package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefense;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearBounceMixin {
    @Unique
    private static boolean mismatchedleggings$sgBounceFallReady() {
        return false;
    }

    @Unique
    private static boolean mismatchedleggings$sgRetaliationReady() {
        return false;
    }

    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=fall-level"), require = 0, expect = 0, allow = 1)
    private static int mismatchedleggings$sgBounceFallLevel(int original, LivingFallEvent event) {
        return SilentGearDefense.fallLevel(original, event, mismatchedleggings$sgBounceFallReady());
    }

    @ModifyExpressionValue(method = "onPlayerHurt", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=retaliation-level"), require = 0, expect = 0, allow = 1)
    private static int mismatchedleggings$sgBounceRetaliationLevel(int original, LivingDamageEvent.Post event) {
        return SilentGearDefense.retaliationLevel(original, event, mismatchedleggings$sgRetaliationReady());
    }
}
