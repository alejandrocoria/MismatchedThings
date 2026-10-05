package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false)
abstract class IceAndFireDamageMixin {
    // Zero is intentional: a late local-contract mismatch stays inert even with injector debug validation enabled.
    @WrapOperation(
            method = "onEntityDamage(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireDamageFeetInjectionPoint",
                    remap = false),
            require = 0, expect = 0, allow = 3)
    private static boolean mismatchedboots$recognizeArmorBootSource(
            Object item, Operation<Boolean> original, LivingDamageEvent.Pre event) {
        // Preserve each native class operation: one Troll piece and one Scale/Steel piece, not two halves.
        return original.call(item) || MixedBootsSources.anySource(
                event.getEntity().getItemBySlot(EquipmentSlot.FEET), source -> original.call(source.getItem()));
    }
}
