package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.silentgear.VoidWardChecks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class VoidWardWitnessMixin {
    @WrapOperation(method = "onPlayerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"), require = 1, allow = 1)
    private static void witness(Player player, double x, double y, double z, Operation<Void> original) {
        VoidWardChecks.push(player, x, y, z);
        original.call(player, x, y, z);
    }
}
