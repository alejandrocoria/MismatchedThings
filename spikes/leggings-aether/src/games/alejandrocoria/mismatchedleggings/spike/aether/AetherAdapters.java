package games.alejandrocoria.mismatchedleggings.spike.aether;

import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AetherAdapters {
    private AetherAdapters() {}

    public static int count(int original, LivingEntity wearer, TagKey<Item> requested) {
        var root = wearer.getItemBySlot(EquipmentSlot.LEGS);
        // Only add the piece the native owner-component comparison did not already count.
        return root.get(AetherIIDataComponents.ARMOR_SET) != requested
                && any(root, source -> source.get(AetherIIDataComponents.ARMOR_SET) == requested) ? original + 1 : original;
    }

    public static boolean tier(boolean original, ItemStack root, Supplier<?> supplier) {
        return original || supplier.get() == AetherIIDataComponents.REINFORCEMENT_TIER.get()
                && any(root, source -> source.has(AetherIIDataComponents.REINFORCEMENT_TIER));
    }

    public static boolean any(ItemStack root, Predicate<ItemStack> predicate) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        return data != null && (predicate.test(data.left().source()) || predicate.test(data.right().source()));
    }

    public static boolean managed(ItemStack root) {
        return MixedLeggingsStructure.validData(root).isPresent();
    }
}
