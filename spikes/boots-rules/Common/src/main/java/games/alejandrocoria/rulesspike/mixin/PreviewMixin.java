package games.alejandrocoria.rulesspike.mixin;

import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import games.alejandrocoria.rulesspike.PreviewState;
import games.alejandrocoria.rulesspike.Proof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.Level;

@Mixin(MixedBootsWorkbenchRecipe.class)
abstract class PreviewMixin {
    @Inject(method = "assemblePreview", at = @At("HEAD"), cancellable = true)
    private void snapshotPreview(WorkbenchInput input, Level level, CallbackInfoReturnable<WorkbenchResult> callback) {
        if (Proof.CANDIDATE) {
            var snapshot = PreviewState.current();
            callback.setReturnValue(snapshot == null ? WorkbenchResult.EMPTY
                    : new MixedBootsWorkbenchRecipe(snapshot.attributes(), snapshot.components()).assemblePair(input));
        }
    }
}
