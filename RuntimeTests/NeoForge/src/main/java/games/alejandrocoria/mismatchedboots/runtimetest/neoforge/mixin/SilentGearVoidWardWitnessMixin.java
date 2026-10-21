package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRemainingSpike;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native invocation/push witnesses only: production owns mixed recognition. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearVoidWardWitnessMixin {
    @Inject(method = "onPlayerTick", at = @At("HEAD"))
    private static void query(PlayerTickEvent.Pre event, CallbackInfo ci) {
        var player = event.getEntity();
        if (!player.level().isClientSide() && player.getY() < player.level().getMinY() - 32) { SilentGearRemainingSpike.queries++; }
    }

    @WrapOperation(method = "onPlayerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
    private static void observe(Player player, double x, double y, double z, Operation<Void> original) {
        SilentGearRemainingSpike.pushes++;
        original.call(player, x, y, z);
    }
}
