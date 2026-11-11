package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeBounce;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearDefenseSpikeBounceMixin {
    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeSite", args = "site=fall-level"), require = 1, allow = 1)
    private static int mismatchedleggings$spikeFallLevel(int original, LivingFallEvent event) {
        return SilentGearDefenseSpikeBounce.level(original, event.getEntity(), true);
    }

    @ModifyExpressionValue(method = "onPlayerHurt", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeSite", args = "site=retaliation-level"), require = 1, allow = 1)
    private static int mismatchedleggings$spikeRetaliationLevel(int original, LivingDamageEvent.Post event) {
        return SilentGearDefenseSpikeBounce.level(original, event.getEntity(), false);
    }
}
