package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearVoidWard;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearVoidWardMixin {
    @Unique
    private static boolean mismatchedleggings$sgVoidReady() {
        return false;
    }

    @ModifyExpressionValue(method = "onPlayerTick", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearVoidWardInjectionPoint"), require = 0, expect = 0, allow = 1)
    private static boolean mismatchedleggings$sgVoidWard(boolean original, PlayerTickEvent.Pre event) {
        return SilentGearVoidWard.recognize(original, event, mismatchedleggings$sgVoidReady());
    }
}
