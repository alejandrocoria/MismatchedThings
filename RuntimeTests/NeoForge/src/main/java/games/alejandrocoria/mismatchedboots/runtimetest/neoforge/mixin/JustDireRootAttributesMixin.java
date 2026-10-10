package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRootUpgradePrototype;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JustDireAttributesCompatibility.class, remap = false)
abstract class JustDireRootAttributesMixin {
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private static void prepare(ItemStack previous, ItemStack output, AttributeRuleSet rules, CallbackInfo callback) {
        JustDireRootUpgradePrototype.prepare(previous, output, rules);
        callback.cancel();
    }

    @Inject(method = "project", at = @At("HEAD"), cancellable = true)
    private static void project(ItemStack root, ItemAttributeModifiers defaults, CallbackInfoReturnable<ItemAttributeModifiers> callback) {
        callback.setReturnValue(JustDireRootUpgradePrototype.project(root, defaults));
    }
}
