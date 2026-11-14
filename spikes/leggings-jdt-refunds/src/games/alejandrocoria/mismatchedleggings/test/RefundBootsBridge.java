package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedleggings.test.refundspike.SharedConsumption;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Loaded only in graphs containing current Boots. Its real credits/binding/completion stay unchanged. */
final class RefundBootsBridge {
    static void register(Item item) {
        SharedConsumption.register(item, (root, count, owner, ordinary) ->
                JustDireRefundCompatibility.route(true, root, count, owner, ordinary));
        SkeletonChecks.require(JustDireRefundCompatibility.available(), "Real Boots refund binding absent");
    }

    static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedBootsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    static List<String> credits(ItemStack root) {
        var value = root.get(JustDireRefundCompatibility.component());
        return value == null ? List.of() : value.names();
    }
}
