package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefense;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.event.GearEvents", remap = false)
abstract class SilentGearSafeFallMixin {
    @Unique private static boolean mismatchedboots$sgSafeFallReady() { return false; }

    @Inject(method = "onLivingFall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSuppressingBounce()Z"), cancellable = true, require = 0, expect = 0, allow = 1)
    private static void mismatchedboots$sgFallGate(LivingFallEvent event, CallbackInfo callback) {
        if (!SilentGearDefense.allowFall(event, mismatchedboots$sgSafeFallReady())) { callback.cancel(); }
    }

    @WrapWithCondition(method = "onLivingFall", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseInjectionPoint", args = "site=wear"), require = 0, expect = 0, allow = 1)
    private static boolean mismatchedboots$sgFallWear(ItemStack stack, int amount, LivingEntity actor, EquipmentSlot slot) {
        return SilentGearDefense.allowNativeFallWear(stack, amount, actor, slot, mismatchedboots$sgSafeFallReady());
    }
}
