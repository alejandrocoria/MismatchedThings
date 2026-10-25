package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalGuardControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.api.util.AugmentUtils", remap = false)
abstract class MysticalEquipmentPeerMixin {
    @WrapOperation(method = "getArmorAugments", at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/mysticalagriculture/api/util/AugmentUtils;getAugments(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<Object> peer(ItemStack stack, Operation<List<Object>> original) {
        MysticalGuardControl.equipmentCalls++;
        return original.call(stack);
    }
}
