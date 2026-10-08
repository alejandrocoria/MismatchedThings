package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireJumpGuardPlugin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.events.LivingEntityEvents", priority = 2000, remap = false)
abstract class JustDireJumpCompatibleMixin {
    @WrapOperation(method = "jumpEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack jump(Player player, EquipmentSlot slot, Operation<ItemStack> original) {
        JustDireJumpGuardPlugin.jumpReads++;
        return original.call(player, slot);
    }
    @WrapOperation(method = "LivingFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack fall(ServerPlayer player, EquipmentSlot slot, Operation<ItemStack> original) {
        JustDireJumpGuardPlugin.fallReads++;
        return original.call(player, slot);
    }
}
