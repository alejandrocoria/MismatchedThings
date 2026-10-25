package games.alejandrocoria.mismatchedboots.mixin.compatibility.mysticalagriculture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.api.util.AugmentUtils", remap = false)
abstract class MysticalEquipmentMixin {
    @Unique
    private static boolean mismatchedboots$maEquipmentReady() {
        return false;
    }

    @WrapOperation(method = "getArmorAugments", at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/mysticalagriculture/api/util/AugmentUtils;getAugments(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"), require = 0, expect = 0)
    private static List<Object> mismatchedboots$maEquipment(ItemStack stack, Operation<List<Object>> original, Player player) {
        var result = original.call(stack);
        return mismatchedboots$maEquipmentReady() ? MysticalAugmentsCompatibility.equipment(player, stack, result) : result;
    }
}
