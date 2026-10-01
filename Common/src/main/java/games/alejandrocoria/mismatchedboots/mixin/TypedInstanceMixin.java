package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsBehaviorTags;
import net.minecraft.core.TypedInstance;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TypedInstance.class)
interface TypedInstanceMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
    private void mismatchedboots$projectAuditedBehaviorTag(
            TagKey<?> tag,
            CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())
                || !((Object) this instanceof ItemStack stack)) {
            return;
        }
        if (MixedBootsBehaviorTags.projects(stack, tag)) {
            callback.setReturnValue(true);
        }
    }
}
