package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireCooldownHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Method;

/** Fault only the HUD-local lookup, leaving native rendering/settings/server metadata intact. */
@Mixin(JustDireCooldownHud.class)
abstract class JustDireCooldownHudMetadataMissingMixin {
    @WrapOperation(method = "profile", at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object fixtureParameters(Method method, Object receiver, Object[] args, Operation<Object> original) {
        if (((Enum<?>) args[0]).name().equals("EARTHQUAKE")) {
            throw new IllegalStateException("Simulated unavailable Earthquake HUD-only native parameters");
        }
        return original.call(method, receiver, args);
    }
}
