package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireInventoryActivationSpike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spike only: all action/transaction logic stays production; widen its location guard in one scoped call. */
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility", remap = false)
abstract class JustDireInventoryLocationMixin {
    @Inject(method = "equipped", at = @At("HEAD"), cancellable = true)
    private static void location(ServerPlayer player, ItemStack root, CallbackInfoReturnable<Boolean> cir) {
        if (JustDireInventoryActivationSpike.scoped(root)) { cir.setReturnValue(JustDireInventoryActivationSpike.located(player, root)); }
    }

    @WrapOperation(method = "activate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void wear(ItemStack root, int amount, LivingEntity owner, EquipmentSlot slot, Operation<Void> original) {
        if (!JustDireInventoryActivationSpike.breakAtLocation(root, amount)) { original.call(root, amount, owner, slot); }
    }
}
