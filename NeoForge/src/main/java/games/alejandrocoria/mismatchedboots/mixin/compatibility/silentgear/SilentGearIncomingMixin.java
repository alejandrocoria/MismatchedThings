package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefense;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearIncomingMixin {
    @Unique private static boolean mismatchedboots$sgIncomingReady() { return false; }

    @ModifyExpressionValue(method = "onLivingIncomingDamage", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=armor"), require = 0, expect = 0, allow = 1)
    private static boolean mismatchedboots$sgIncomingArmor(boolean original, LivingIncomingDamageEvent event,
            @Local EquipmentSlot slot, @Local(ordinal = 1) ItemStack stack) {
        return SilentGearDefense.armor(original, event, slot, stack, mismatchedboots$sgIncomingReady());
    }

    @WrapOperation(method = "onLivingIncomingDamage", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=incoming"), require = 0, expect = 0, allow = 1)
    private static Object mismatchedboots$sgIncomingCall(ItemStack stack, Object current, @Coerce Object function, Operation<Object> original,
            LivingIncomingDamageEvent event, @Local(ordinal = 0) float base) {
        // The final-flow proof relates these captured locals to the actual armor stack and lambda base.
        return SilentGearDefense.incoming(stack, current, function, original, event, base, mismatchedboots$sgIncomingReady());
    }
}
