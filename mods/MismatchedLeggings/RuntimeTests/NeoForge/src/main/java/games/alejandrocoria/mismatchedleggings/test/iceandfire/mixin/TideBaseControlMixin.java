package games.alejandrocoria.mismatchedleggings.test.iceandfire.mixin;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value = Item.class, priority = 800)
abstract class TideBaseControlMixin {}
