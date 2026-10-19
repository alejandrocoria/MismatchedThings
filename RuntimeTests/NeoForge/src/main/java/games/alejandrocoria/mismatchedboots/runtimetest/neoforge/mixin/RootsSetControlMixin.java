package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsTickTestControl;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "elucent.rootsclassic.event.ComponentSpellsEvent", remap = false)
abstract class RootsSetControlMixin {
    @WrapOperation(method = "wildwoodArmorRegenFullset", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextDouble()D"))
    private double rollHeal(RandomSource random, Operation<Double> original) {
        Integer forced = RootsTickTestControl.roll(0);
        return forced == null ? original.call(random) : forced.doubleValue();
    }
}
