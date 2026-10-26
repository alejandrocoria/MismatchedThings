package games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly;

import com.zurrtum.create.AllItems;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.zurrtum.create.content.equipment.armor.CardboardArmorHandler", remap = false)
abstract class CreateFlyCardboardArmorHandlerMixin {
    @Inject(
            method = "isCardboardArmor(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void mismatchedboots$recognizeCardboardSource(
            ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            return;
        }
        if (MixedBootsSources.anySource(
                stack, source -> source.getItem() == AllItems.CARDBOARD_BOOTS)) {
            callback.setReturnValue(true);
        }
    }
}
