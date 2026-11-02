package games.alejandrocoria.mismatchedthings.internal.client.tooltip;

import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

public final class NeoForgeTooltipSeparators {
    private NeoForgeTooltipSeparators() {
    }

    public static void registerFactory(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SeparatorImage.class, marker -> TooltipSeparatorComponent.INSTANCE);
    }

    public static void gather(RenderTooltipEvent.GatherComponents event) {
        var elements = event.getTooltipElements();
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).left().orElse(null) instanceof TooltipSeparator) {
                elements.set(index, Either.right(SeparatorImage.INSTANCE));
            }
        }
    }

    private enum SeparatorImage implements TooltipComponent {
        INSTANCE
    }
}
