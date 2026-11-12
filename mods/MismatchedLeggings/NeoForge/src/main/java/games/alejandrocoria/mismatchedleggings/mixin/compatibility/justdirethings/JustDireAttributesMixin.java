package games.alejandrocoria.mismatchedleggings.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CommonHooks.class)
abstract class JustDireAttributesMixin {
    // Final transformed data-flow proof, not a hook-seen flag or native-mod presence.
    @Unique
    private static boolean mismatchedleggings$justDireAttributesReady() {
        return false;
    }

    @ModifyArg(method = "computeModifiedAttributes", index = 1, require = 0, expect = 0, allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/ItemAttributeModifierEvent;<init>(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/ItemAttributeModifiers;)V"))
    private static ItemAttributeModifiers mismatchedleggings$justDireAttributes(ItemStack root, ItemAttributeModifiers defaults) {
        return mismatchedleggings$justDireAttributesReady() ? JustDireAttributes.project(root, defaults) : defaults;
    }
}
