package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearer;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerReadiness;
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
abstract class SilentGearSetMixin implements SilentGearWearerReadiness {
    @Override public boolean mismatchedboots$sgSetReady() { return mismatchedboots$sgWielderReady(); }
    @Unique private static boolean mismatchedboots$sgWielderReady() { return false; }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=type"), require = 0, expect = 0, allow = 1)
    private boolean mismatchedboots$sgSetType(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                            @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || mismatchedboots$sgWielderReady() && SilentGearWearer.predicate(false, context, slot, stack);
    }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=trait"), require = 0, expect = 0, allow = 1)
    private boolean mismatchedboots$sgSetTrait(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                             @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || mismatchedboots$sgWielderReady() && SilentGearWearer.predicate(false, context, slot, stack);
    }
}
