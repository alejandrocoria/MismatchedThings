package games.alejandrocoria.mismatchedboots.mixin.compatibility.justdirethings;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.containers.ToolSettingContainer", remap = false)
abstract class JustDireSettingsContainerMixin {
    @Inject(method = "getItemSlots(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/transfer/ResourceHandler;",
            at = @At("HEAD"), cancellable = true, require = 0, expect = 0, allow = 1)
    private void mismatchedboots$emptySettingsInventory(ItemStack stack,
                                                        CallbackInfoReturnable<ResourceHandler<ItemResource>> callback) {
        // JDT opens this menu even with an empty hand; ItemAccess.forStack rejects EMPTY.
        // Null is the menu's existing no-internal-slots result. Real stacks keep native behavior.
        if (stack.isEmpty()) {
            callback.setReturnValue(null);
        }
    }
}
