package games.alejandrocoria.mismatchedleggings.test.mystical.mixin;

import games.alejandrocoria.mismatchedleggings.test.mystical.MysticalCandidate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
abstract class ArmorTickCandidate {
    @Inject(method = "inventoryTick", at = @At("RETURN"))
    private void spike$tick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        MysticalCandidate.tick(stack, level, entity, slot);
    }
}
