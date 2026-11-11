package games.alejandrocoria.mismatchedleggings.test.silentgear.remaining;

import games.alejandrocoria.mismatchedleggings.NeoForgeMixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearRemainingCandidate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeoForgeMixedLeggingsItem.class)
abstract class RemainingTickMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void remaining(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        SilentGearRemainingCandidate.tick(root, level, entity, slot);
    }
}
