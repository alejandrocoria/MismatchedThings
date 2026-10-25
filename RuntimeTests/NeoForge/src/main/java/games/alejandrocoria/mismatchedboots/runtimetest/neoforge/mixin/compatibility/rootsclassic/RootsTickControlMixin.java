package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.rootsclassic;

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
@Mixin(targets = {"elucent.rootsclassic.item.SylvanArmorItem", "elucent.rootsclassic.item.WildwoodArmorItem"}, remap = false)
abstract class RootsTickControlMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void noteTick(CallbackInfo callback) {
        RootsTickTestControl.tick();
    }

    @WrapOperation(method = "inventoryTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"), require = 0, expect = 0)
    private int rollTerra(RandomSource random, int bound, Operation<Integer> original) {
        Integer forced = RootsTickTestControl.roll(bound);
        return forced == null ? original.call(random, bound) : forced;
    }
}
