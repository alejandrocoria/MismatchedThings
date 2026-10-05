package games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.zurrtum.create.content.kinetics.belt.BeltBlock", remap = false)
abstract class CreateFlyBeltBlockMixin {
    @WrapOperation(
            method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V",
            at = @At(
                    value = "games.alejandrocoria.mismatchedboots.compatibility.createfly.CreateFlyBeltInjectionPoint",
                    remap = false),
            require = 0, expect = 0, allow = 1)
    private boolean mismatchedboots$recognizeCardboardSourceOnBelt(
            ItemStack boots, Object exactItem, Operation<Boolean> original) {
        // The local selector guarantees CARDBOARD_BOOTS. Do not capture a player-local ordinal
        // or reevaluate another mod's chained predicate on stored source stacks.
        return original.call(boots, exactItem)
                || MixedBootsSources.anySource(boots, source -> source.getItem() == exactItem);
    }
}
