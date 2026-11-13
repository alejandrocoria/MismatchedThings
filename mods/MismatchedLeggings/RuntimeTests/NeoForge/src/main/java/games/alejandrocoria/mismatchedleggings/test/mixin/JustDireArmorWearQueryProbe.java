package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.JustDireArmorWearChecks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercise each real local query independently; never modify shared permission or registration. */
@Pseudo
@Mixin(targets = {"games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireResources",
        "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility"}, remap = false)
abstract class JustDireArmorWearQueryProbe {
    @Inject(method = "wearProfile", at = @At("HEAD"), require = 1)
    private static void query(ItemStack root, CallbackInfoReturnable<?> callback) {
        JustDireArmorWearChecks.query(root);
    }
}
