package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsTickTestControl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "elucent.rootsclassic.util.RootsUtil", remap = false)
abstract class RootsTickHelperFaultMixin {
    @Inject(method = "randomlyRepair", at = @At("HEAD"))
    private static void foreignConsumer(RandomSource random, ItemStack stack, CallbackInfo callback) {
        RootsTickTestControl.foreignStack(stack);
    }
}
