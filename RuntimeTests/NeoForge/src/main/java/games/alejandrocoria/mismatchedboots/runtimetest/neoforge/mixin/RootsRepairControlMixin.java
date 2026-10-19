package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsTickTestControl;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "elucent.rootsclassic.util.RootsUtil", remap = false)
abstract class RootsRepairControlMixin {
    @Inject(method = "randomlyRepair", at = @At("HEAD"))
    private static void noteRepair(CallbackInfo callback) { RootsTickTestControl.repair(); }

    @WrapOperation(method = "randomlyRepair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private static int rollRepair(RandomSource random, int bound, Operation<Integer> original) {
        Integer forced = RootsTickTestControl.roll(bound);
        return forced == null ? original.call(random, bound) : forced;
    }
}
