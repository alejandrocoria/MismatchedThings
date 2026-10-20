package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearDefenseSpike;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Enabled only for defense-spike. Native control paths remain available with candidate=false. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearDefenseSpikeMixin {
    @ModifyExpressionValue(method = "onLivingFall", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/silentchaos512/gear/util/TraitHelper;getHighestLevelArmorOrCurio(Lnet/minecraft/world/entity/player/Player;Lnet/silentchaos512/gear/api/util/DataResource;)I"))
    private static int sgtest$fallLevel(int original, LivingFallEvent event) {
        return Math.max(original, SilentGearDefenseSpike.bounce(event.getEntity().getItemBySlot(EquipmentSlot.FEET)));
    }

    @ModifyExpressionValue(method = "onPlayerHurt", at = @At(value = "INVOKE",
            target = "Lnet/silentchaos512/gear/util/TraitHelper;getHighestLevelArmor(Lnet/minecraft/world/entity/player/Player;Lnet/silentchaos512/gear/api/util/DataResource;)I"))
    private static int sgtest$attackerLevel(int original, LivingDamageEvent.Post event) {
        return Math.max(original, SilentGearDefenseSpike.bounce(event.getEntity().getItemBySlot(EquipmentSlot.FEET)));
    }

}
