package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearEffectReadiness;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.SelfRepairTraitEffect", remap = false)
abstract class SilentGearSelfRepairMixin implements SilentGearEffectReadiness {
    @Unique public boolean mismatchedboots$sgRepairReady() { return false; }

    @WrapWithCondition(method = "inventoryTick", require = 0, expect = 0, allow = 1,
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRepairInjectionPoint"))
    private boolean mismatchedboots$sgRepairCondition(ItemStack stack, int amount, LivingEntity entity, InteractionHand hand) {
        return !SilentGearEffects.captureRepair(stack, amount, entity);
    }
}
