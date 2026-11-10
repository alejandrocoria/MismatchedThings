package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect", remap = false)
abstract class SilentGearSetMixin {
    @Unique
    private static boolean mismatchedleggings$sgWielderReady() { return false; }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=type"), require = 0)
    private boolean mismatchedleggings$sgSetType(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                               @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || mismatchedleggings$sgWielderReady() && SilentGearWearer.recognizes(context, slot, stack);
    }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=trait"), require = 0)
    private boolean mismatchedleggings$sgSetTrait(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                                @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || mismatchedleggings$sgWielderReady() && SilentGearWearer.recognizes(context, slot, stack);
    }
}
