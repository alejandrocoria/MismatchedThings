package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

final class AetherSources {
    private AetherSources() {}

    static boolean any(ItemStack root, Predicate<ItemStack> predicate) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        return data != null && (predicate.test(data.left().source()) || predicate.test(data.right().source()));
    }
}
