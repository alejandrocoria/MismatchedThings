package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherAlkahestSpikeControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.AetherIIEventListeners", remap = false)
abstract class AetherAlkahestArmorSpikeMixin {
    @WrapOperation(method = "onArmorDamaged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;has(Ljava/util/function/Supplier;)Z"), require = 1)
    private static boolean protection(ItemStack stack, Supplier<?> component, Operation<Boolean> original) {
        return AetherAlkahestSpikeControl.tier(original.call(stack, component), stack, component, true);
    }
}
