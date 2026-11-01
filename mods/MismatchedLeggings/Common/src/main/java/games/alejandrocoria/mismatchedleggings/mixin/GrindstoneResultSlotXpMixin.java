package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsGrindstoneXp;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// 26.1.2 keeps the per-input XP helper in the native result slot, GrindstoneMenu$4.
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
abstract class GrindstoneResultSlotXpMixin {
    @ModifyReturnValue(method = "getExperienceFromItem(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private int mismatchedleggings$boundPotential(int nativePotential, ItemStack input) {
        return LeggingsGrindstoneXp.adjustPotential(input, nativePotential);
    }
}
