package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireGroundStompLifecycleSpike;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises the known local preservation candidate inside real smithing, not after taking the result. */
@Mixin(MixedBootsRecomposer.class)
abstract class JustDireActivePreservationSpikeMixin {
    @Inject(method = "recompose(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private static void preserve(ItemStack previous, ItemStack left, ItemStack right, CallbackInfoReturnable<ItemStack> result) {
        JustDireGroundStompLifecycleSpike.preserve(previous, result.getReturnValue());
    }
}
