package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsBridge;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.api.util.AugmentUtils", remap = false)
abstract class AugmentEquipmentMixin {
    @Inject(method = "getArmorAugments", at = @At("RETURN"), cancellable = true)
    private static void mixed(Player player, CallbackInfoReturnable<List<Object>> callback) {
        callback.setReturnValue(MysticalAugmentsBridge.armor(player, callback.getReturnValue()));
    }
}
