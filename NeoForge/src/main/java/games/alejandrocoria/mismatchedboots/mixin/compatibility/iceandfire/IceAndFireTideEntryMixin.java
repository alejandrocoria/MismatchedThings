package games.alejandrocoria.mismatchedboots.mixin.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideReadiness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
abstract class IceAndFireTideEntryMixin implements IceAndFireTideReadiness {
    @Override
    public boolean mismatchedboots$tideEntryReady() {
        return false;
    }
}
