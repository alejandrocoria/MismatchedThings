package games.alejandrocoria.mismatchedboots.compatibility.aether;

import games.alejandrocoria.mismatchedboots.client.compatibility.aether.AetherTooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Consumer;

/** The registered item also runs on dedicated servers; resolve client bindings only behind both gates. */
public final class AetherTooltipCompatibility {
    private AetherTooltipCompatibility() {}
    private static boolean available() { return FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("aether_ii"); }
    public static void source(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (available()) { AetherTooltipClient.sourceTier(source, flag, output); }
    }
    public static void root(ItemStack root, TooltipDisplay display, TooltipFlag flag, Consumer<Component> output) {
        if (available()) { AetherTooltipClient.root(root, display, flag, output); }
    }
}
