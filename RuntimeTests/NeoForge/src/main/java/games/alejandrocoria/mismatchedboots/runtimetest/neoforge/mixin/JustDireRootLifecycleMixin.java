package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.platform.NeoForgePlatformHelper;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRootUpgradePrototype;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NeoForgePlatformHelper.class, remap = false)
abstract class JustDireRootLifecycleMixin {
    @Inject(method = "finalizeMixedBootsCreation", at = @At("HEAD"))
    private void create(ItemStack output, AttributeRuleSet rules, CallbackInfo callback) {
        JustDireRootUpgradePrototype.initialize(output);
    }

    @Inject(method = "finalizeMixedBootsRecomposition", at = @At("HEAD"))
    private void preserve(ItemStack previous, ItemStack output, AttributeRuleSet rules, CallbackInfo callback) {
        JustDireRootUpgradePrototype.preserve(previous, output);
    }
}
