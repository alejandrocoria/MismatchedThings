package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsBehaviorTags;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherAlkahestSpikeControl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MixedBootsBehaviorTags.class)
abstract class AetherAlkahestTagSpikeMixin {
    @Inject(method = "projects", at = @At("RETURN"), cancellable = true)
    private static void resistance(ItemStack stack, TagKey<?> tag, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(AetherAlkahestSpikeControl.tag(callback.getReturnValue(), stack, tag));
    }
}
