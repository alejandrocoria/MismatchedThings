package games.alejandrocoria.mismatchedthings.test;

import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

final class SharedTooltipChecks {
    private SharedTooltipChecks() {
    }

    static void verify(Minecraft client) {
        var before = Component.literal("Before separator");
        var after = Component.literal("After separator");
        var rows = new ArrayList<Either<FormattedText, TooltipComponent>>(List.of(
                Either.left(before), Either.left(TooltipSeparator.INSTANCE), Either.left(after)));
        var event = new RenderTooltipEvent.GatherComponents(ItemStack.EMPTY, 960, 540, rows, -1);
        NeoForge.EVENT_BUS.post(event);
        var gathered = event.getTooltipElements();
        WorkbenchChecks.require(gathered.size() == 3 && gathered.getFirst().left().orElse(null) == before
                        && gathered.getLast().left().orElse(null) == after,
                "Standalone separator conversion reordered or replaced unrelated text");
        var component = ClientTooltipComponent.create(gathered.get(1).right().orElseThrow());
        WorkbenchChecks.require(component.getHeight(client.font) == 10 && component.getWidth(client.font) == 0,
                "Standalone separator factory was not registered");
    }
}
