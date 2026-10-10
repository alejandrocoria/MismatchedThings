package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRootUpgradePrototype;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpCompatibility$Api", remap = false)
abstract class JustDireRootJumpQueryMixin {
    @Inject(method = "value", at = @At("HEAD"))
    private void overlay(ItemStack source, ItemStack root, CallbackInfoReturnable<Integer> callback) {
        JustDireRootUpgradePrototype.overlay(source, root);
    }
}
