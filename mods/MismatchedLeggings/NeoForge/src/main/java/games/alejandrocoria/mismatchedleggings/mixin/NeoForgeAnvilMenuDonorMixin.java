package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsRepair;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
abstract class NeoForgeAnvilMenuDonorMixin {
    @ModifyExpressionValue(
            method = "createResultInternal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean mismatchedleggings$acceptSourceDonor(boolean normallyAccepted) {
        var menu = (AnvilMenu) (Object) this;
        return LeggingsRepair.anvilDonor(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(),
                menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem(), normallyAccepted);
    }

    @ModifyExpressionValue(
            method = "createResultInternal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;canStoreEnchantments(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mismatchedleggings$requireValidInput(boolean normallyAccepted) {
        var menu = (AnvilMenu) (Object) this;
        return LeggingsRepair.anvilInput(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(), normallyAccepted);
    }
}

