package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsEnchantments;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
abstract class AnvilMenuEnchantMixin {
    @ModifyExpressionValue(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mismatchedleggings$sourceAdmission(boolean normallyAllowed, @Local Enchantment enchantment) {
        var input = ((AnvilMenu) (Object) this).getSlot(AnvilMenu.INPUT_SLOT).getItem();
        return MixedLeggingsStructure.isMixedItem(input)
                ? LeggingsEnchantments.canApply(enchantment, input) : normallyAllowed;
    }
}
