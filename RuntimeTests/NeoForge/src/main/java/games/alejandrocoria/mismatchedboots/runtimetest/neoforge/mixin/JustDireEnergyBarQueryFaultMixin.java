package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeJustDireItemBarsTest;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in native query faults on mixed arguments only; original boots and storage remain real. */
@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.items.armors.CelestigemBoots", remap = false)
abstract class JustDireEnergyBarQueryFaultMixin {
    @Inject(method = "isBarVisible", at = @At("HEAD"))
    private void visibility(ItemStack root, CallbackInfoReturnable<Boolean> callback) {
        if (!NeoForgeJustDireItemBarsTest.faultArmed(root)) { return; }
        switch (System.getProperty("mismatchedboots.runtimetest.justDireItemBarsVariant", "")) {
            case "queryFailure" -> {
                NeoForgeJustDireItemBarsTest.faultObserved();
                throw new IllegalStateException("Intentional native bar query failure");
            }
            case "linkageFailure" -> {
                NeoForgeJustDireItemBarsTest.faultObserved();
                throw new NoSuchMethodError("Intentional native bar linkage failure");
            }
        }
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void width(ItemStack root, CallbackInfoReturnable<Integer> callback) {
        if (NeoForgeJustDireItemBarsTest.faultArmed(root)
                && System.getProperty("mismatchedboots.runtimetest.justDireItemBarsVariant", "").equals("invalidWidth")) {
            NeoForgeJustDireItemBarsTest.faultObserved(); callback.setReturnValue(14);
        }
    }
}
