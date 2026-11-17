package games.alejandrocoria.mismatchedleggings.test.neovitaeupdate;

import games.alejandrocoria.mismatchedleggings.test.NeoVitaeUpdateSpike;
import net.minecraft.core.TypedInstance;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Candidate-only equivalent of one audited whitelist entry in each product. */
@Mixin(TypedInstance.class)
interface TagsMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
    private void research$eligibility(TagKey<?> tag, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && Boolean.getBoolean("nv.spike.share")
                && (Object) this instanceof ItemStack stack && NeoVitaeUpdateSpike.projects(stack, tag)) {
            callback.setReturnValue(true);
        }
    }
}
