package games.alejandrocoria.mismatchedleggings.test.iafspike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.test.iafspike.Candidate;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
abstract class TideCandidate {
    @ModifyExpressionValue(method = "inventoryTick", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.iafspike.LegsPoint", args = "kind=tide"), require = 0, expect = 0, allow = 1)
    private boolean iafSpike$tide(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || Candidate.tide(entity);
    }
}
