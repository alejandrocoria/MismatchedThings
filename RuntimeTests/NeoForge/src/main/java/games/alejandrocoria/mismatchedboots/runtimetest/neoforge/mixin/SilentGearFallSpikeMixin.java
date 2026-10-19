package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearFallSpikeMixin {
    @WrapOperation(method = "onLivingFall", at = @At(value = "INVOKE", target =
            "Lnet/silentchaos512/gear/util/TraitHelper;getHighestLevelArmorOrCurio(Lnet/minecraft/world/entity/player/Player;Lnet/silentchaos512/gear/api/util/DataResource;)I"))
    private static int recognizeMoonwalker(Player player, @Coerce Object trait, Operation<Integer> original) throws Exception {
        return SilentGearTraitSpike.fallLevel(player, trait, original.call(player, trait));
    }
}
