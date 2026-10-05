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
abstract class IceAndFireDragonDamageMixin {
    // Zero is intentional: a late fingerprint mismatch must stay inert even with injector debug validation enabled.
    @WrapOperation(
            method = "onEntityDamage(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireDragonFeetInjectionPoint",
                    remap = false),
            require = 0, expect = 0, allow = 2)
    private static boolean mismatchedboots$recognizeDragonBootSource(
            Object item, Operation<Boolean> original, LivingDamageEvent.Pre event) {
        // Each operation retains its actual upstream class check. The enclosing OR counts feet only once.
        return original.call(item) || MixedBootsSources.anySource(
                event.getEntity().getItemBySlot(EquipmentSlot.FEET), source -> original.call(source.getItem()));
    }
}
