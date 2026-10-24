package games.alejandrocoria.mismatchedboots.mixin.compatibility.iceandfire;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false)
abstract class IceAndFireLightningSetMixin {
    // Guard failure is intentionally inert, including under injector debug validation.
    @WrapOperation(
            method = "onLivingHurt(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireLightningFeetInjectionPoint",
                    remap = false),
            require = 0, expect = 0, allow = 1)
    private static boolean mismatchedboots$recognizeLightningSetBoot(
            ItemStack feet, Object exactItem, Operation<Boolean> original) {
        // The native argument is the exact registered Lightning boot; other slots and damage selection stay native.
        return original.call(feet, exactItem)
                || MixedBootsSources.anySource(feet, source -> original.call(source, exactItem));
    }
}
