package games.alejandrocoria.entryspike.mixin;

import games.alejandrocoria.entryspike.EntryChecks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse the existing client launch, hidden-window hooks and world; no second launch driver. */
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.test.SkeletonClientChecks", remap = false)
abstract class TickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void entrySpike$tick(Minecraft client, CallbackInfo callback) {
        EntryChecks.tick(client);
        callback.cancel();
    }
}
