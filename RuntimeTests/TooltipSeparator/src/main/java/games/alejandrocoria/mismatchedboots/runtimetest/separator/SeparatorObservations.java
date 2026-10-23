package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only witness of production rendering, never a marker producer, factory or renderer. */
public final class SeparatorObservations {
    public static final List<Integer> WIDTHS = new ArrayList<>();
    public static final List<Draw> DRAWS = new ArrayList<>();
    public static ItemStack input;
    public static boolean copyText;
    public static Optional<TooltipComponent> image = Optional.empty();

    private SeparatorObservations() {}

    public static boolean enabled() { return Boolean.getBoolean("mismatchedboots.test.separatorTest"); }

    public record Draw(boolean separator, int y, int height) {}
}
