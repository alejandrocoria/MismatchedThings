package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.WearSpike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireResources$LiveHandler")
abstract class WearSpikeDebitMixin {
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int partial(int amount) {
        return WearSpike.partialReceipt ? Math.max(0, amount - 1) : amount;
    }

    @Inject(method = "extract", at = @At("HEAD"), require = 1)
    private void replace(int amount, TransactionContext transaction, CallbackInfoReturnable<Integer> ci) {
        if (WearSpike.replacementPlayer != null) {
            var player = WearSpike.replacementPlayer;
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy());
        }
    }
}
