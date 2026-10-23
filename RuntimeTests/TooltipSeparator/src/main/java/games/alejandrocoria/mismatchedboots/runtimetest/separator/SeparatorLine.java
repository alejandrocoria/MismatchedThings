package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.ArrayList;
import java.util.List;

/** Test-only full-width, constant-color row; the renderer owns width and position. */
public final class SeparatorLine implements ClientTooltipComponent {
    public static final int COLOR = 0xFF707070;
    public static final List<Integer> WIDTHS = new ArrayList<>();
    public static int preservedSplits;

    @Override public int getHeight(Font font) { return 10; }
    @Override public int getWidth(Font font) { return 0; }
    @Override public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        if (width <= 0) { throw new AssertionError("Separator received no tooltip width"); }
        WIDTHS.add(width);
        graphics.fill(x, y + 4, x + width, y + 5, COLOR);
    }
}
