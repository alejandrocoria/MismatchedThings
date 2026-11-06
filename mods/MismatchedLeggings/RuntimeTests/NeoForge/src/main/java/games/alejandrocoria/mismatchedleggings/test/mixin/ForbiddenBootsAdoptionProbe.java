package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.ForbiddenBootsStorage;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe completed production conversions, never replace the persistent codec or count native name probes. */
@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers", remap = false)
abstract class ForbiddenBootsAdoptionProbe {
    @Inject(method = "adopt", at = @At("RETURN"))
    private static void leggings$countAdoption(ItemStack input, CallbackInfoReturnable<ItemStack> callback) {
        if (input != callback.getReturnValue()) {
            ForbiddenBootsStorage.adoptions.incrementAndGet();
        }
    }
}
