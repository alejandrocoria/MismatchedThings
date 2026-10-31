package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

final class SharedTooltipChecks {
    private SharedTooltipChecks() {
    }

    static void verify(Minecraft client) {
        var rows = client.font.split(TooltipSeparator.INSTANCE, 200);
        WorkbenchChecks.require(rows.size() == 1 && rows.getFirst() == TooltipSeparator.Sequence.INSTANCE,
                "Standalone separator lost its identity while wrapping");
        var component = ClientTooltipComponent.create(rows.getFirst());
        WorkbenchChecks.require(component.getHeight(client.font) == 10 && component.getWidth(client.font) == 0,
                "Standalone separator renderer was not installed");
    }
}
