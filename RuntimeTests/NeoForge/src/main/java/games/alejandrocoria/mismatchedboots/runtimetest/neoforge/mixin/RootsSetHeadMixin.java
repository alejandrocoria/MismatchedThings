package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Independent optional-slot adapter witness, not a second mixed-armor project. */
@Pseudo
@Mixin(targets = {"elucent.rootsclassic.item.StaffItem", "elucent.rootsclassic.item.CrystalStaffItem"}, remap = false)
abstract class RootsSetHeadMixin {
    @WrapOperation(method = "releaseUsing", at = @At(value =
            "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsHeadInjectionPoint"))
    private boolean acceptHead(Object item, Operation<Boolean> original) {
        return original.call(item) || item == Items.DIAMOND_HELMET;
    }
}
