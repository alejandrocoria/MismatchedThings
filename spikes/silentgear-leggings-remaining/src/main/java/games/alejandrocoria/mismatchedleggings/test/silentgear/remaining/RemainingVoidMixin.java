package games.alejandrocoria.mismatchedleggings.test.silentgear.remaining;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearRemainingCandidate;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class RemainingVoidMixin {
    @Unique private static boolean mismatchedleggings$remainingReady() { return false; }

    @ModifyExpressionValue(method = "onPlayerTick", at = @At(value = "INVOKE", target = "Lnet/silentchaos512/gear/util/TraitHelper;hasTraitArmor(Lnet/minecraft/world/entity/player/Player;Lnet/silentchaos512/gear/api/util/DataResource;)Z"), require = 1, allow = 1)
    private static boolean mismatchedleggings$remainingVoid(boolean original, PlayerTickEvent.Pre event) {
        return SilentGearRemainingCandidate.voidWard(original, event, mismatchedleggings$remainingReady());
    }
}
