package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerReadiness;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearWearerFixture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Opt-in candidate: preserve the native loop and compose only its two local predicate results. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect", remap = false)
abstract class SilentGearSetSpikeMixin implements SilentGearWearerReadiness {
    @Override public boolean mismatchedboots$sgSetReady() { return false; }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=type"), require = 0, expect = 0, allow = 1)
    private boolean mismatchedboots$sgSetType(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                            @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) throws Throwable {
        return SilentGearWearerFixture.predicate(this, original, context, slot, stack);
    }

    @ModifyExpressionValue(method = "getSetPieceCount", at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearSetInjectionPoint", args = "predicate=trait"), require = 0, expect = 0, allow = 1)
    private boolean mismatchedboots$sgSetTrait(boolean original, @Coerce Object context, @Coerce Object type, Player player,
                                             @Local(index = 6) EquipmentSlot slot, @Local(index = 7) ItemStack stack) throws Throwable {
        return SilentGearWearerFixture.predicate(this, original, context, slot, stack);
    }
}
