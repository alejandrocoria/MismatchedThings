package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import net.minecraft.core.component.DataComponentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JustDireAttributesCompatibility.class)
abstract class JustDireAttributeApiMissingMixin {
    @ModifyReturnValue(method = "component", at = @At("RETURN"))
    private static DataComponentType<?> missing(DataComponentType<?> original) {
        return null;
    }
}
