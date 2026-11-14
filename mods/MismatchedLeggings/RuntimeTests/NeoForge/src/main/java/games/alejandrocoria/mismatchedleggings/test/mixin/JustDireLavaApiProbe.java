package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireLavaRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Real lookup failures inside the production binding's catch boundaries, never forced readiness. */
@Mixin(JustDireLavaRepair.class)
public abstract class JustDireLavaApiProbe {
    @ModifyVariable(method = "id", at = @At("HEAD"), argsOnly = true)
    private static String missingItem(String path) {
        return System.getProperty("leggings.test.lavaFault", "").equals("api") && path.equals("blazegold_leggings")
                ? "missing_lava_provider" : path;
    }

    @ModifyArg(method = "bind", at = @At(value = "INVOKE", target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"), index = 0)
    private static String missingParticles(String name) {
        return System.getProperty("leggings.test.lavaFault", "").equals("particles") && name.endsWith(".ItemFlowParticleData")
                ? name + "Missing" : name;
    }
}
