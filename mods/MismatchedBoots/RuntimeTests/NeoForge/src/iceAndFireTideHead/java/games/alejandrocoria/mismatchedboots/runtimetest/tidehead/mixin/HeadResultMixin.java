package games.alejandrocoria.mismatchedboots.runtimetest.tidehead.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.tidehead.TideHeadFixture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
abstract class HeadResultMixin {
    @ModifyExpressionValue(method = "inventoryTick", at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.tidehead.TideSlotInjectionPoint", args = "slot=HEAD"), require = 0, expect = 0, allow = 1)
    private boolean tideHead$head(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || TideHeadFixture.recognizes(entity, EquipmentSlot.HEAD);
    }
}
