package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalGuardControl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.augment.JumpBoostAugment", remap = false)
abstract class MysticalJumpPeerMixin {
    @WrapOperation(method = "onArmorTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean peer(Player player, MobEffectInstance effect, Operation<Boolean> original) {
        MysticalGuardControl.armorCalls++;
        return original.call(player, effect);
    }
}
