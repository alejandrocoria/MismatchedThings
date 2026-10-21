package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherRecognitionTestControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.EquipmentUtil", remap = false)
abstract class AetherSetResultControlMixin {
    @ModifyExpressionValue(method = "getArmorCount", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private static Object observe(Object original) { return AetherRecognitionTestControl.setResult(original); }
}
