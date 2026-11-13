package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.test.WearSpikeCandidate;
import games.alejandrocoria.mismatchedleggings.test.WearSpikePlugin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireArmorWearCompatibility", remap = false)
abstract class WearSpikeBridgeMixin {
    @Unique
    private static boolean leggingswearspike$wearReady() {
        return false;
    }

    @Inject(method = "route", at = @At("HEAD"), cancellable = true, require = 0, expect = 0, allow = 1)
    private static void leggingswearspike$bridge(boolean ready, ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                                                Operation<Void> original, float originalDamage, CallbackInfo ci) {
        if (ready && leggingswearspike$wearReady() && WearSpikePlugin.commonReady && slot == EquipmentSlot.LEGS) {
            WearSpikeCandidate.route(true, root, amount, entity, slot, original, originalDamage);
            ci.cancel();
        }
    }
}
