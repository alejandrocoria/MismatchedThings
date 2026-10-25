package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
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
abstract class SuspendedBootsAnvilMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$onlyRename(CallbackInfo callback) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack addition = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
        if (ModContent.suspendedBootsData(addition).isPresent()
                || ModContent.suspendedBootsData(input).isPresent() && !addition.isEmpty()) {
            menu.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
            cost.set(0);
            callback.cancel();
        }
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mismatchedboots$retainRenameShell(CallbackInfo callback) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (ModContent.suspendedBootsData(input).isEmpty() || result.isEmpty()
                || !menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty()) { return; }
        // Native rename also writes empty enchantments and repair cost. Neither is a live-shell edit:
        // retain the exact suspended patch and transfer only the name, so reload cannot invent a conflict.
        ItemStack renamed = input.copy();
        var name = result.get(DataComponents.CUSTOM_NAME);
        if (name == null) { renamed.remove(DataComponents.CUSTOM_NAME); }
        else { renamed.set(DataComponents.CUSTOM_NAME, name); }
        menu.getSlot(AnvilMenu.RESULT_SLOT).set(renamed);
    }
}
