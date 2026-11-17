package games.alejandrocoria.mismatchedleggings.test.neovitaeupdate;

import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import games.alejandrocoria.mismatchedleggings.test.NeoVitaeUpdateSpike;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Candidate compatibility reader: an absent old root marker is not an explicit removal. */
@Mixin(HellforgedHelper.class)
abstract class HellforgedMixin {
    @Inject(method = "isHellforged", at = @At("RETURN"), cancellable = true)
    private static void research$legacy(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.getBoolean("nv.spike.legacyReader") && !callback.getReturnValue()
                && !stack.has(NVDataComponents.HELLFORGED.get()) && NeoVitaeUpdateSpike.inheritsHellforged(stack)) {
            callback.setReturnValue(true);
        }
    }
}
