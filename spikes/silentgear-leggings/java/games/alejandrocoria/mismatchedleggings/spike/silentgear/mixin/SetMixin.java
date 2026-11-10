package games.alejandrocoria.mismatchedleggings.spike.silentgear.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.spike.silentgear.SetAccess;
import games.alejandrocoria.mismatchedleggings.spike.silentgear.SetCandidate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value = WielderEffectTraitEffect.class, remap = false)
abstract class SetMixin implements SetAccess {
    @Unique
    private static boolean leggings$sgSetProofReady() {
        return false; // Published only after the final transformed local slice is checked.
    }

    @Override
    public boolean spike$ready() {
        return leggings$sgSetProofReady();
    }

    @Override
    @Invoker("getSetPieceCount")
    public abstract int spike$count(TraitActionContext context, GearType type, Player player);

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedleggings.spike.silentgear.SetPoint", args = "predicate=type"), require = 0)
    private boolean leggings$sgSetProofType(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                         @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || leggings$sgSetProofReady() && SetCandidate.recognizes(context, slot, stack);
    }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedleggings.spike.silentgear.SetPoint", args = "predicate=trait"), require = 0)
    private boolean leggings$sgSetProofTrait(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                          @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) {
        return original || leggings$sgSetProofReady() && SetCandidate.recognizes(context, slot, stack);
    }
}
