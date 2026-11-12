package games.alejandrocoria.jdtspike.mixin;

import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ToolSettingContainer.class, remap = false)
abstract class EmptyMenuMixin {
    @Inject(method = "getItemSlots", at = @At("HEAD"), cancellable = true)
    private void empty(ItemStack stack, CallbackInfoReturnable<ResourceHandler<ItemResource>> ci) {
        if (stack.isEmpty()) { ci.setReturnValue(null); }
    }
}
