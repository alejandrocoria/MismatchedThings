package games.alejandrocoria.mismatchedleggings.mixin.compatibility.createfly;

import com.zurrtum.create.AllItems;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
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
    private static void mismatchedleggings$recognizeCardboardSource(
            ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            return;
        }

        // Add only our piece: native consumers and optional sibling hooks own the rest of the set.
        var data = MixedLeggingsStructure.validData(stack);
        if (data.isPresent()
                && (data.get().left().source().is(AllItems.CARDBOARD_LEGGINGS)
                || data.get().right().source().is(AllItems.CARDBOARD_LEGGINGS))) {
            callback.setReturnValue(true);
        }
    }
}
