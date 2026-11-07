package games.alejandrocoria.mismatchedboots.compatibility.neovitae;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeInfusionService;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;

/** Boots owns source extraction; the shared core owns native binding and the coherent infusion rule. */
public final class NeoVitaeInfusionCompatibility {
    private NeoVitaeInfusionCompatibility() {
    }

    /** Retained bootstrap entry point; Things now registers the shared binding once. */
    public static void initialize(IEventBus bus) {
    }

    public static void initializeCreation(ItemStack output) {
        var data = ModContent.getMixedBootsData(output).orElse(null);
        if (data != null) {
            NeoVitaeInfusionService.INSTANCE.initializeCreation(output, data.left().source(), data.right().source());
        }
    }

    public static void preserveState(ItemStack previous, ItemStack output) {
        NeoVitaeInfusionService.INSTANCE.preserveState(previous, output);
    }
}
