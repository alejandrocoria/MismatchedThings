package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {"net.silentchaos512.gear.event.GearEvents",
                  "net.silentchaos512.gear.gear.trait.effect.NegateDamageTraitEffect"}, priority = 900, remap = false)
abstract class SilentGearDefensePeerMixin {
    @Unique
    private static boolean sgtest$defensePeer() {
        return true;
    }
}
