package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import net.minecraft.core.component.DataComponentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JustDireResourcesCompatibility.class)
abstract class JustDireResourceApiMissingMixin {
    @ModifyReturnValue(method = "energyComponent", at = @At("RETURN"))
    private static DataComponentType<Integer> missingBinding(DataComponentType<Integer> original) {
        return null;
    }
}
