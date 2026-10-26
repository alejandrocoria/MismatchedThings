package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearVoidWardMixin {
    @Unique
    private static boolean mismatchedboots$sgVoidReady() {
        return false;
    }

    @ModifyExpressionValue(method = "onPlayerTick", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearVoidWardInjectionPoint"), require = 0, expect = 0, allow = 1)
    private static boolean mismatchedboots$sgVoidWard(boolean original, PlayerTickEvent.Pre event) {
        return SilentGearRecognition.voidWard(original, event, mismatchedboots$sgVoidReady());
    }
}
