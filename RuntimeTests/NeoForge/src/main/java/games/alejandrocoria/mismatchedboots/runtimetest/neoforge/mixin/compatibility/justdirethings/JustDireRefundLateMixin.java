package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ItemStack.class, priority = 800)
abstract class JustDireRefundLateMixin {
    @Unique
    private static void refundTest$lateMarker() {
    }
}
