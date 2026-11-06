package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounded test-only failure: a successful item decode must survive optional native API incompatibility. */
@Mixin(targets = "games.alejandrocoria.mismatchedthings.internal.compatibility.forbiddenarcanus.NeoForgeForbiddenModifierService", remap = false)
public abstract class ForbiddenBootsAdoptionFault {
    @Inject(method = "applyExisting", at = @At("HEAD"))
    private void leggings$refuseAdoption(ItemStack output, CallbackInfoReturnable<ItemStack> callback) {
        if (Boolean.getBoolean("leggings.test.failForbiddenAdoption")) {
            throw new IllegalArgumentException("Expected test-only native adoption API incompatibility");
        }
    }
}
