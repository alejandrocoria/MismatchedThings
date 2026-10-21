package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.client.compatibility.silentgear.SilentGearTooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Consumer;

/** The item also exists on dedicated servers: client bindings are resolved only behind both gates. */
public final class SilentGearTooltipCompatibility {
    private SilentGearTooltipCompatibility() {}

    public static void appendSource(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (FMLEnvironment.getDist() == Dist.CLIENT && ModList.get().isLoaded("silentgear")) {
            SilentGearTooltipClient.appendSource(source, flag, output);
        }
    }
}
