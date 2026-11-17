package games.alejandrocoria.mismatchedleggings.test.silentgear.atm;

import games.alejandrocoria.mismatchedleggings.test.silentgear.AtmCompatChecks;
import games.alejandrocoria.mismatchedleggings.test.silentgear.FoundationChecks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Selects this bounded proof instead of running unrelated foundation cases. */
@Mixin(value = FoundationChecks.class, remap = false)
abstract class AtmDriverMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void atm$run(MinecraftServer server, CallbackInfo callback) {
        AtmCompatChecks.tick(server);
        callback.cancel();
    }
}
