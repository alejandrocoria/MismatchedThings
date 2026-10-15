package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRefundSpikeCandidate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Independent real wrapper: the fixture verifies that its after-shrink fault precedes candidate completion. */
@Mixin(value = ItemStack.class, priority = 900)
abstract class JustDireRefundSpikeForeignMixin {
    @WrapOperation(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void refundSpike$foreign(ItemStack root, int count, Operation<Void> original, @Local(argsOnly = true) LivingEntity owner) {
        JustDireRefundSpikeCandidate.foreignBefore(root);
        original.call(root, count);
        JustDireRefundSpikeCandidate.foreignAfter(root, owner);
    }
}
