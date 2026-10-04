package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsGrindstoneXpPolicy;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Minecraft 26.1.2 puts the result slot's private per-input XP helper in GrindstoneMenu$4.
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
abstract class GrindstoneResultSlotXpMixin {
    @ModifyReturnValue(
            method = "getExperienceFromItem(Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("RETURN"))
    private int mismatchedboots$boundMixedPotential(int vanillaPotential, ItemStack input) {
        return MixedBootsGrindstoneXpPolicy.adjustPotential(input, vanillaPotential);
    }
}
