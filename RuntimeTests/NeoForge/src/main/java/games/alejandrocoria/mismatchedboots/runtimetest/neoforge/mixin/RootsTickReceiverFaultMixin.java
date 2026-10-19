package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsTickTestControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Late foreign stack consumer: native controls stay functional, but passing a mixed root must be refused. */
@Pseudo
@Mixin(targets = "elucent.rootsclassic.item.SylvanArmorItem", remap = false)
abstract class RootsTickReceiverFaultMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void foreignConsumer(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo callback) {
        RootsTickTestControl.foreignStack(stack);
    }
}
