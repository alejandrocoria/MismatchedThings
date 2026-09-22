package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsAnvilPolicy;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
abstract class AnvilMenuDonorMixin {
    @ModifyExpressionValue(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z",
                    ordinal = 0))
    private boolean mismatchedboots$acceptSourceDonor(boolean normallyAccepted) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack addition = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
        return MixedBootsAnvilPolicy.canUseOrdinaryDonor(input, addition, normallyAccepted);
    }
}
