package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.tide.TideCarrierProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The real superclass is transformed. Native ordinary ticks must still execute this hook. */
@Mixin(value = Item.class, priority = 500)
abstract class TideBaseChangeMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void tideTest$changedBase(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        TideCarrierProbe.baseTick(stack);
    }
}
