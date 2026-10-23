package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorClientSpike;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorLine;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorMarker;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

final class SeparatorNeoForgeSpike {
    static void register(IEventBus bus) {
        bus.addListener((RegisterClientTooltipComponentFactoriesEvent event) -> event.register(SeparatorMarker.class, marker -> new SeparatorLine()));
        NeoForge.EVENT_BUS.addListener((RenderTooltipEvent.GatherComponents event) -> {
            var elements = event.getTooltipElements();
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).left().orElse(null) instanceof SeparatorMarker marker) { elements.set(i, Either.right(marker)); }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SeparatorClientSpike.tick(Minecraft.getInstance()));
    }
}
