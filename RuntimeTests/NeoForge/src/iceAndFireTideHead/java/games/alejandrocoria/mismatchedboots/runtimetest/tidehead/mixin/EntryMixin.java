package games.alejandrocoria.mismatchedboots.runtimetest.tidehead.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.tidehead.Ready;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
abstract class EntryMixin implements Ready {
    @Override public boolean tideHead$entryReady() { return false; }
}
