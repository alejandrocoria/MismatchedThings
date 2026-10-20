package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearDefenseSpike;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Enabled only for defense-spike. Native control paths remain available with candidate=false. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearDefenseSpikeMixin {
    @ModifyExpressionValue(method = "onLivingIncomingDamage", at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearDefenseInjectionPoint"))
    private static boolean sgtest$armor(boolean original, @Local(index = 10) EquipmentSlot slot, @Local(index = 11) ItemStack stack) {
        return original || SilentGearDefenseSpike.candidate && slot == EquipmentSlot.FEET && SilentGearDefenseSpike.eligible(stack);
    }

    @WrapOperation(method = "onLivingIncomingDamage", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/silentchaos512/gear/util/TraitHelper;activateTraits(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Object;Lnet/silentchaos512/gear/api/traits/TraitFunction;)Ljava/lang/Object;"))
    private static Object sgtest$incoming(ItemStack stack, Object current, @Coerce Object function, Operation<Object> original,
                                         @Local(argsOnly = true) LivingIncomingDamageEvent event) {
        return SilentGearDefenseSpike.candidate && SilentGearDefenseSpike.eligible(stack)
                ? SilentGearDefenseSpike.incoming(stack, (Float) current, event) : original.call(stack, current, function);
    }

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
