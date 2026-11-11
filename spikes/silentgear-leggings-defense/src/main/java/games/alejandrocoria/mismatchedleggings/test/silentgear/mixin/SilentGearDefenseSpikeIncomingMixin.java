package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpike;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearDefenseSpikeIncomingMixin {
    @ModifyExpressionValue(method = "onLivingIncomingDamage", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeSite", args = "site=armor"), require = 1, allow = 1)
    private static boolean mismatchedleggings$spikeIncomingArmor(boolean original, LivingIncomingDamageEvent event,
                                                                 @Local EquipmentSlot slot, @Local(ordinal = 1) ItemStack stack) {
        return SilentGearDefenseSpike.armor(original, event, slot, stack);
    }

    @WrapOperation(method = "onLivingIncomingDamage", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeSite", args = "site=incoming"), require = 1, allow = 1)
    private static Object mismatchedleggings$spikeIncomingCall(ItemStack stack, Object current, @Coerce Object function,
                                                               Operation<Object> original, LivingIncomingDamageEvent event,
                                                               @Local(ordinal = 0) float base) {
        return SilentGearDefenseSpike.incoming(stack, current, function, original, event, base);
    }
}
