package games.alejandrocoria.mismatchedboots.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** The active tooltip renderer owns layout and width; this row only decorates the existing gap. */
public final class TooltipSeparatorComponent implements ClientTooltipComponent {
    public static final TooltipSeparatorComponent INSTANCE = new TooltipSeparatorComponent();
    private static final int HEIGHT = 10;
    private static final int OFFSET = 4;
    private static final int COLOR = 0xFF707070;

    private TooltipSeparatorComponent() {}

    @Override public int getHeight(Font font) { return HEIGHT; }
    @Override public int getWidth(Font font) { return 0; }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        if (width > 0) { graphics.fill(x, y + OFFSET, x + width, y + OFFSET + 1, COLOR); }
    }
}
