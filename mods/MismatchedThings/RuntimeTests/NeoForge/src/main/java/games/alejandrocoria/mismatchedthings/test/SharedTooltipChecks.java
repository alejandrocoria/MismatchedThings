package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

final class SharedTooltipChecks {
    private SharedTooltipChecks() {
    }

    static void verify(Minecraft client) {
        var component = ClientTooltipComponent.create(TooltipSeparator.INSTANCE);
        WorkbenchChecks.require(component.getHeight(client.font) == 10 && component.getWidth(client.font) == 0,
                "Standalone separator factory was not registered");
    }
}
