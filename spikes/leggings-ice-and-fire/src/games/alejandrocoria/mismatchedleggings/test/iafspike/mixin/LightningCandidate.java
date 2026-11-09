package games.alejandrocoria.mismatchedleggings.test.iafspike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.iafspike.Candidate;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false)
abstract class LightningCandidate {
    @WrapOperation(method = "onLivingHurt", at = @At(value = "games.alejandrocoria.mismatchedleggings.test.iafspike.LegsPoint", args = "kind=lightning"), require = 0, expect = 0, allow = 1)
    private static boolean iafSpike$lightning(ItemStack stack, Object expected, Operation<Boolean> original) {
        return original.call(stack, expected) || Candidate.any(stack, source -> original.call(source, expected));
    }
}
