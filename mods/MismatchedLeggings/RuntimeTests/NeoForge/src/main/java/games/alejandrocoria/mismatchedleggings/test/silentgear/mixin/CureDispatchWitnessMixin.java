package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.CureChecks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCureApi", remap = false)
abstract class CureDispatchWitnessMixin {
    @Inject(method = "tick", at = @At("RETURN"), require = 1)
    private void afterNative(Object effect, Object instance, ItemStack copy, ServerLevel level, Player player, CallbackInfo ci) {
        CureChecks.afterNative(effect, instance, copy, player);
    }
}
