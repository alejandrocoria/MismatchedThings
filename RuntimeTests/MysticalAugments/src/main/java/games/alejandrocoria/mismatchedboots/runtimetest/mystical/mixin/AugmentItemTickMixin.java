package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsBridge;
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
abstract class AugmentItemTickMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void mixed(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo callback) {
        MysticalAugmentsBridge.tick(stack, level, entity, slot);
    }
}
