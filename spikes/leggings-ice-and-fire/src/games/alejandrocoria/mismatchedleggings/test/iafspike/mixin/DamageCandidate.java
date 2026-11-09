package games.alejandrocoria.mismatchedleggings.test.iafspike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.iafspike.Candidate;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false)
abstract class DamageCandidate {
    @WrapOperation(method = "onEntityDamage", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.iafspike.LegsPoint", args = "kind=damage"), require = 0, expect = 0, allow = 3)
    private static boolean iafSpike$damage(Object item, Operation<Boolean> original, LivingDamageEvent.Pre event) {
        return original.call(item) || Candidate.any(event.getEntity().getItemBySlot(EquipmentSlot.LEGS), source -> original.call(source.getItem()));
    }
}
