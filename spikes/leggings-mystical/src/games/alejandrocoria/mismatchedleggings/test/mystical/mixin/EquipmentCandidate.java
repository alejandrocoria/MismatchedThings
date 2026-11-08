package games.alejandrocoria.mismatchedleggings.test.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.mystical.MysticalCandidate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(targets = "com.blakebr0.mysticalagriculture.api.util.AugmentUtils", remap = false)
abstract class EquipmentCandidate {
    @WrapOperation(method = "getArmorAugments", at = @At(value = "INVOKE", target = "Lcom/blakebr0/mysticalagriculture/api/util/AugmentUtils;getAugments(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<Object> spike$equipment(ItemStack stack, Operation<List<Object>> original, Player player) {
        return MysticalCandidate.equipment(player, stack, original.call(stack));
    }
}
