package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.events.LivingEntityEvents", priority = 2000, remap = false)
abstract class JustDireJumpMissingFallMixin {
    @Redirect(method = "LivingFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack replaceFallRead(ServerPlayer player, EquipmentSlot slot) {
        return player.getItemBySlot(slot);
    }
}
