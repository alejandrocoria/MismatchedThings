package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRefundSpikeCandidate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemStack.class, priority = 1_000)
abstract class JustDireRefundSpikeMixin {
    @WrapOperation(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void refundSpike$actualBreak(ItemStack root, int count, Operation<Void> original, @Local(argsOnly = true) LivingEntity owner) {
        var capture = JustDireRefundSpikeCandidate.capture(root, count, owner);
        original.call(root, count);
        JustDireRefundSpikeCandidate.complete(capture);
    }
}
