package games.alejandrocoria.mismatchedleggings.mixin.compatibility.justdirethings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods", remap = false)
@Pseudo
abstract class JustDireMovementMixin {
    @Unique
    private static boolean mismatchedleggings$jdtMovementReady() { return false; }
}
