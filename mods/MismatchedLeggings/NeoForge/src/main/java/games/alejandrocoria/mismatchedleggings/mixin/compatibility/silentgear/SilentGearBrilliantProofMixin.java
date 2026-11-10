package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.item.gear.GearArmorItem", remap = false)
abstract class SilentGearBrilliantProofMixin {
    @Unique
    private static boolean mismatchedleggings$sgBrilliantReady() {
        return false;
    }
}
