package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefense;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearBounceMixin {
    @Unique private static boolean mismatchedboots$sgBounceFallReady() { return false; }
    @Unique private static boolean mismatchedboots$sgRetaliationReady() { return false; }

    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=fall-level"), require = 0, expect = 0, allow = 1)
    private static int mismatchedboots$sgBounceFallLevel(int original, LivingFallEvent event) {
        return SilentGearDefense.fallLevel(original, event, mismatchedboots$sgBounceFallReady());
    }

    @ModifyExpressionValue(method = "onPlayerHurt", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=retaliation-level"), require = 0, expect = 0, allow = 1)
    private static int mismatchedboots$sgBounceRetaliationLevel(int original, LivingDamageEvent.Post event) {
        return SilentGearDefense.retaliationLevel(original, event, mismatchedboots$sgRetaliationReady());
    }
}
