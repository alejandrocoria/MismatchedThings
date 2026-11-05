package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.AllTheModiumChecks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.allthemods.allthemodium.common.items.ModiumLeggingsItem", remap = false)
abstract class AllTheModiumTickProbe {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void observe(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        AllTheModiumChecks.observe(stack);
    }
}
