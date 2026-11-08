package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import net.minecraft.world.item.ItemStack;

/** Boots supplies source histories; Things owns only the native reservoir operation. */
public final class RogdoriumInfusionCompatibility {
    private RogdoriumInfusionCompatibility() {
    }

    public static void initializeCreation(ItemStack output) {
        var data = ModContent.getMixedBootsData(output).orElse(null);
        if (data != null) {
            RogdoriumInfusionService.INSTANCE.initializeCreation(output, data.left().source(), data.right().source());
        }
    }
}
