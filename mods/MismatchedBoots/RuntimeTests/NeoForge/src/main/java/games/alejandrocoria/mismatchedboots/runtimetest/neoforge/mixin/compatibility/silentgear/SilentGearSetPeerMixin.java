package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Independent test peer owns HEAD, not a whole-set replacement or a contribution to our FEET helper. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect", priority = 900, remap = false)
abstract class SilentGearSetPeerMixin {
    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=type"), require = 1)
    private boolean sgtest$headType(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                    @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || slot == EquipmentSlot.HEAD && stack.is(Items.DIAMOND_HELMET);
    }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=trait"), require = 1)
    private boolean sgtest$headTrait(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                     @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || slot == EquipmentSlot.HEAD && stack.is(Items.DIAMOND_HELMET);
    }
}
