package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.RootsRenderingSpike;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Research only: the production JAR and ordinary development client never load this mixin. */
@Mixin(MixedBootsEquipmentRenderer.class)
abstract class RootsRenderingSpikeMixin {
    @ModifyReturnValue(method = "preserveSplit", at = @At("RETURN"))
    private static <S> Model<? super S> rootsCandidate(
            Model<? super S> result, Model<? super S> original, Model<? super S> resolved) {
        return RootsRenderingSpike.adapt(result, resolved);
    }
}
