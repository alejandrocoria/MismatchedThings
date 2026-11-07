package games.alejandrocoria.mismatchedleggings.spike.aether.mixin;

import games.alejandrocoria.mismatchedleggings.spike.aether.AetherAdapters;
import net.minecraft.core.TypedInstance;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TypedInstance.class)
interface BehaviorTagMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
    private void aetherSpike$tag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && !System.getProperty("leggings.test.aetherMode").equals("baseline")
                && tag.location().toString().equals("aether_ii:alkahest_resistant_item")
                && (Object) this instanceof ItemStack stack && AetherAdapters.any(stack, source -> source.is(tag))) {
            result.setReturnValue(true);
        }
    }
}
