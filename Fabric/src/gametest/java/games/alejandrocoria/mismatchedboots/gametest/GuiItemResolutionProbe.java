package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GuiItemResolutionProbe {
    private static Set<Identifier> expectedModels = Set.of();
    private static int expectedCount;
    private static boolean armed;
    private static final List<Resolution> resolutions = new ArrayList<>();

    private GuiItemResolutionProbe() {
    }

    public static void arm(int count, Identifier... models) {
        expectedModels = Set.of(models);
        expectedCount = count;
        resolutions.clear();
        armed = true;
    }

    public static void record(
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed) {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (!armed || !expectedModels.contains(model) || resolutions.size() >= expectedCount) {
            return;
        }
        resolutions.add(new Resolution(model, displayContext, level, owner, seed));
        if (resolutions.size() == expectedCount) {
            armed = false;
        }
    }

    public static boolean isComplete() {
        return expectedCount > 0 && resolutions.size() == expectedCount;
    }

    public static List<Resolution> take() {
        List<Resolution> result = List.copyOf(resolutions);
        resolutions.clear();
        expectedModels = Set.of();
        expectedCount = 0;
        armed = false;
        return result;
    }

    public record Resolution(
            Identifier model,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed) {
    }
}
