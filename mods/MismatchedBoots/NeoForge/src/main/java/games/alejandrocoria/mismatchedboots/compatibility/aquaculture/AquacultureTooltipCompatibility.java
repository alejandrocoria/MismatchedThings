package games.alejandrocoria.mismatchedboots.compatibility.aquaculture;

import games.alejandrocoria.mismatchedboots.client.compatibility.aquaculture.AquacultureTooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Consumer;

/** Resolve optional native client code only after checking both distribution and mod presence. */
public final class AquacultureTooltipCompatibility {
    private AquacultureTooltipCompatibility() {
    }

    public static void appendSource(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("aquaculture")) {
            AquacultureTooltipClient.appendSource(source, flag, output);
        }
    }
}
