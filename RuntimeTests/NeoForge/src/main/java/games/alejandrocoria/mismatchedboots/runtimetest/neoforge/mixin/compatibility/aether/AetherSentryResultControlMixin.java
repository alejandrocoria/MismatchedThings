package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherRecognitionTestControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.armor.abilities.SentryArmor", remap = false)
interface AetherSentryResultControlMixin {
    @ModifyExpressionValue(method = "playerFall", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/Holder;)Z"))
    private static boolean observe(boolean original) {
        return AetherRecognitionTestControl.sentryResult(original);
    }
}
