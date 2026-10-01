package games.alejandrocoria.mismatchedboots.runtimetest.betterendtag.fabric.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.TypedInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TypedInstance.class)
interface CrystaliteTagProjectionMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
    private void mismatchedboots$projectCrystaliteSet(
            TagKey<?> tag,
            CallbackInfoReturnable<Boolean> callback) {
        TagKey<Item> crystaliteSet = TagKey.create(
                Registries.ITEM, Identifier.fromNamespaceAndPath("betterend", "armor_set/crystalite"));
        if (Boolean.TRUE.equals(callback.getReturnValue())
                || !crystaliteSet.equals(tag)
                || !((Object) this instanceof ItemStack stack)
                || !stack.is(ModContent.MIXED_BOOTS_KEY)
                || !(stack.getItem() instanceof MixedBootsItem)) {
            return;
        }
        if (MixedBootsSources.anySource(
                stack, source -> source.is(crystaliteSet))) {
            callback.setReturnValue(true);
        }
    }
}
