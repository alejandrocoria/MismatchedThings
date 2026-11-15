package games.alejandrocoria.mismatchedleggings.test.refunds.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** The plugin damages only MA's owned marker, after its mixin and before final publication. */
@Mixin(value = ItemStack.class, priority = 800)
abstract class MaMarkerFaultMixin {
}
