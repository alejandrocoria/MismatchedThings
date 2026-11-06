package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Supply operation-local registries without changing Things' published recipe ABI. */
@Mixin(targets = "games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu", remap = false)
public abstract class WorkbenchCompositionContextMixin {
    @Shadow
    @Final
    private Player owner;

    @WrapOperation(method = {"updatePreview", "revalidatePreview"}, at = @At(value = "INVOKE",
            target = "Lgames/alejandrocoria/mismatchedthings/api/WorkbenchRecipe;assemblePair(Lgames/alejandrocoria/mismatchedthings/api/WorkbenchInput;)Lgames/alejandrocoria/mismatchedthings/api/WorkbenchResult;"))
    private WorkbenchResult mismatchedleggings$assemble(WorkbenchRecipe recipe, WorkbenchInput input,
                                                       Operation<WorkbenchResult> original) {
        return recipe instanceof MixedLeggingsWorkbenchRecipe leggings
                ? leggings.assemblePair(input, owner.registryAccess()) : original.call(recipe, input);
    }
}
