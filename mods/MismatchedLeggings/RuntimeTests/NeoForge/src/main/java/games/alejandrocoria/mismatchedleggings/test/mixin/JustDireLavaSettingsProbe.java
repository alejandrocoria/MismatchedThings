package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Fail only the optional intrinsic metadata binding, not the core settings API. */
@Mixin(JustDireSettings.class)
public abstract class JustDireLavaSettingsProbe {
    @ModifyArg(method = "bindLava", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getField(Ljava/lang/String;)Ljava/lang/reflect/Field;"), index = 0)
    private static String missingAbility(String name) {
        return name.equals("LAVAREPAIR") ? "MISSING_LAVA_ABILITY" : name;
    }
}
