package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireAttributeTestSupport;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CommonHooks.class, priority = 1500)
abstract class JustDireAttributeEarlierMixin {
    @ModifyArg(method = "computeModifiedAttributes", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/ItemAttributeModifierEvent;<init>(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/ItemAttributeModifiers;)V"))
    private static ItemAttributeModifiers competingDefaults(ItemAttributeModifiers defaults, @Local(argsOnly = true) ItemStack stack) {
        return JustDireAttributeTestSupport.earlier(stack, defaults);
    }
}
