package games.alejandrocoria.mismatchedleggings.mixin.compatibility.iceandfire;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.compatibility.iceandfire.IceAndFireTideCompatibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
abstract class IceAndFireTideLegsMixin {
    @ModifyExpressionValue(method = "inventoryTick", at = @At(value = "games.alejandrocoria.mismatchedleggings.compatibility.iceandfire.IceAndFireTideLegsInjectionPoint"), require = 0, expect = 0, allow = 1)
    private boolean mismatchedleggings$tideLegsResult(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || IceAndFireTideCompatibility.recognizes(entity, EquipmentSlot.LEGS);
    }
}
