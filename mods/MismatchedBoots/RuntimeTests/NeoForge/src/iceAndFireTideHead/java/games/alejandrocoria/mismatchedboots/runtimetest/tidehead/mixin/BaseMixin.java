package games.alejandrocoria.mismatchedboots.runtimetest.tidehead.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.tidehead.Ready;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
abstract class BaseMixin implements Ready.Base {
    @Override
    public boolean tideHead$baseReady() {
        return false;
    }
}
