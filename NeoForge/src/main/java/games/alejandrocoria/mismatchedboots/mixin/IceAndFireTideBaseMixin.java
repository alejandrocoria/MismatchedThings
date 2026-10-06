package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideReadiness;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/** Adds a proof getter, not an override or callback in the base tick. */
@Mixin(Item.class)
abstract class IceAndFireTideBaseMixin implements IceAndFireTideReadiness.Base {
    @Override public boolean mismatchedboots$tideBaseReady() { return false; }
}
