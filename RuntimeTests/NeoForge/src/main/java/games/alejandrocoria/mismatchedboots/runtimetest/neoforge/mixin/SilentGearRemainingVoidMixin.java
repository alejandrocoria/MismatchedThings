package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRemainingSpike;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Research-only local query; native threshold, motion, potions and ordering remain untouched. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearRemainingVoidMixin {
    @ModifyExpressionValue(method = "onPlayerTick", at = @At(value = "INVOKE", target = "Lnet/silentchaos512/gear/util/TraitHelper;hasTraitArmor(Lnet/minecraft/world/entity/player/Player;Lnet/silentchaos512/gear/api/util/DataResource;)Z"))
    private static boolean query(boolean original, PlayerTickEvent.Pre event) {
        SilentGearRemainingSpike.queries++;
        return original || SilentGearRemainingSpike.voidWard(event.getEntity());
    }

    @WrapOperation(method = "onPlayerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
    private static void observe(Player player, double x, double y, double z, Operation<Void> original) {
        SilentGearRemainingSpike.pushes++;
        original.call(player, x, y, z);
    }
}
