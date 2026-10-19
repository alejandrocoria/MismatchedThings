package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scoped feasibility replacement, not the eventual production guard/injection design. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect", remap = false)
abstract class SilentGearSetSpikeMixin {
    @Inject(method = "getSetPieceCount", at = @At("HEAD"), cancellable = true)
    private void countMixedFeet(@Coerce Object context, @Coerce Object type, Player player,
                                CallbackInfoReturnable<Integer> result) throws Exception {
        Integer count = SilentGearTraitSpike.setCount(context, type, player);
        if (count != null) { result.setReturnValue(count); }
    }
}
