package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireAttributeSpike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CommonHooks.class)
abstract class JustDireAttributeSpikeMixin {
    @ModifyArg(method = "computeModifiedAttributes", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/ItemAttributeModifierEvent;<init>(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/ItemAttributeModifiers;)V"))
    private static ItemAttributeModifiers liveDefaults(ItemAttributeModifiers defaults, @Local(argsOnly = true) ItemStack stack) {
        return JustDireAttributeSpike.project(stack, defaults);
    }
}
