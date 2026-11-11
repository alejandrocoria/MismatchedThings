package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.EffectsChecks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.util.GearData", remap = false)
abstract class RecalculationWitnessMixin {
    @Inject(method = "recalculateGearData", at = @At("HEAD"))
    private static void observe(ItemStack stack, Player player, CallbackInfo ci) {
        games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipObservation.recalculation();
        games.alejandrocoria.mismatchedleggings.test.silentgear.BrilliantChecks.recalculation();
        EffectsChecks.recalculation(stack);
        games.alejandrocoria.mismatchedleggings.test.silentgear.WielderChecks.recalculation();
        games.alejandrocoria.mismatchedleggings.test.silentgear.LiveStateChecks.recalculation(stack);
        games.alejandrocoria.mismatchedleggings.test.silentgear.MagnetDispatchProbe.recalculation();
    }
}
