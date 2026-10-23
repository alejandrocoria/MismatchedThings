package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/** Empty text fallback: losing marker identity loses decoration, not tooltip content. */
public final class SeparatorMarker implements Component, TooltipComponent {
    private static final Component EMPTY = Component.empty();
    public static boolean inserting = true;

    public static boolean enabled() { return Boolean.getBoolean("mismatchedboots.test.separatorSpike"); }

    @Override public Style getStyle() { return Style.EMPTY; }
    @Override public ComponentContents getContents() { return EMPTY.getContents(); }
    @Override public List<Component> getSiblings() { return List.of(); }
    @Override public FormattedCharSequence getVisualOrderText() { return Sequence.INSTANCE; }

    public enum Sequence implements FormattedCharSequence {
        INSTANCE;
        @Override public boolean accept(FormattedCharSink sink) { return true; }
    }
}
