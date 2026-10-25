package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A real transformed native recipe produces an extra component beyond the audited installed flag. */
@Mixin(targets = "com.direwolf20.justdirethings.datagen.recipes.AbilityRecipe", remap = false)
abstract class JustDireRootUpgradeRecipeChangedMixin {
    @ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private ItemStack changed(ItemStack original) {
        if (original.isEmpty()) {
            return original;
        }
        var changed = original.copy();
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("Changed native recipe fixture"));
        return changed;
    }
}
