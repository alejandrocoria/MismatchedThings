package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedboots.tooltip.TooltipSeparator;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** Collects the real production tooltip in a dedicated-server JVM, without touching client rendering. */
public final class SeparatorServerProbe {
    private SeparatorServerProbe() {
    }

    public static void run(MinecraftServer server) {
        try {
            var pair = new MixedBootsWorkbenchRecipe().assemblePair(new WorkbenchInput(
                    new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
            if (!pair.isComplete()) {
                throw new AssertionError("Server recipe rejected");
            }

            var root = pair.primary();
            var before = root.copy();
            var lines = root.getTooltipLines(Item.TooltipContext.of(server.overworld()), null, TooltipFlag.NORMAL);
            if (lines.stream().filter(TooltipSeparator.class::isInstance).count() != 2
                    || lines.stream().filter(TooltipSeparator.class::isInstance).anyMatch(line -> !line.getString().isEmpty())
                    || !ItemStack.matches(before, root)) {
                throw new AssertionError("Server tooltip boundaries/fallback/immutability failed");
            }
            NetworkAcceptanceFiles.write("separator-server-success.txt", "production markers collected in dedicated server\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("separator", failure);
        } finally {
            server.halt(false);
        }
    }
}
