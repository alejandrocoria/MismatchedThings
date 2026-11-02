package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The fixture sees actual viewer queries, without putting either optional API on ordinary startup paths. */
public interface ViewerProbe {
    boolean ready();
    List<Cycle> cycles(String namespace);
    void open(String namespace);

    default void verifyOutputTooltips(String namespace) {
    }

    default void verifyCategories(boolean legacyBoots) {
    }

    record Cycle(List<ItemStack> left, List<ItemStack> right, List<ItemStack> primary, List<ItemStack> inverse) {
    }
}
