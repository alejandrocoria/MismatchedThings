package games.alejandrocoria.mismatchedleggings.test.iafspike.mixin;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.test.iafspike.Candidate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MixedLeggingsItem.class)
abstract class TickCandidate {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void iafSpike$tick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo callback) {
        Candidate.tick(stack, level, entity, slot);
    }
}
