package games.alejandrocoria.mismatchedboots.tooltip;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/** Transient section boundary with an ordinary blank-line fallback; safe for server tooltip collection. */
public final class TooltipSeparator implements Component, TooltipComponent {
    public static final TooltipSeparator INSTANCE = new TooltipSeparator();

    private TooltipSeparator() {}

    @Override public Style getStyle() { return Style.EMPTY; }
    @Override public ComponentContents getContents() { return CommonComponents.EMPTY.getContents(); }
    @Override public List<Component> getSiblings() { return List.of(); }
    @Override public FormattedCharSequence getVisualOrderText() { return Sequence.INSTANCE; }

    public enum Sequence implements FormattedCharSequence {
        INSTANCE;
        @Override public boolean accept(FormattedCharSink sink) { return true; }
    }
}
