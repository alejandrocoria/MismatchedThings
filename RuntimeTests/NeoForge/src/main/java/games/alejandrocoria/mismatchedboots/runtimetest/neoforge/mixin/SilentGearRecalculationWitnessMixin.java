package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearEffectsTest;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.util.GearData", remap = false)
abstract class SilentGearRecalculationWitnessMixin {
    @Inject(method = "recalculateGearData", at = @At("HEAD"))
    private static void observe(ItemStack stack, Player player, CallbackInfo ci) {
        SilentGearTraitSpike.recalculation(stack);
        SilentGearEffectsTest.recalculation(stack);
        games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRecognitionTest.recalculation(stack);
        games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLiveStateTest.recalculation(stack);
    }
}
