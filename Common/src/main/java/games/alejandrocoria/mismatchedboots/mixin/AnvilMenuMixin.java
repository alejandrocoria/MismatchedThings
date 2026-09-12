package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.interaction.MixedBootsAnvilPolicy;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
    @Shadow
    @Final
    private DataSlot cost;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$rejectMixedCombination(CallbackInfo callback) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        if (MixedBootsAnvilPolicy.rejectsCombination(
                menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(),
                menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem())) {
            menu.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
            cost.set(0);
            menu.broadcastChanges();
            callback.cancel();
        }
    }
}
