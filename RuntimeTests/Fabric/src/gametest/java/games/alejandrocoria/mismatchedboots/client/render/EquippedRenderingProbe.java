package games.alejandrocoria.mismatchedboots.client.render;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.model.Model;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;

public final class EquippedRenderingProbe {
    private static final List<Call> CALLS = new ArrayList<>();

    private EquippedRenderingProbe() {
    }

    public static synchronized void reset() {
        CALLS.clear();
    }

    public static synchronized void record(
            ResourceKey<EquipmentAsset> asset,
            Model<?> model,
            ItemStack stack,
            int outlineColor) {
        MixedBootsEquipmentRenderer.Side side = model instanceof MixedBootsEquipmentRenderer.SplitModel split
                                                ? split.mismatchedboots$side()
                                                : null;
        if (side == null && !ModContent.isMixedBoots(stack)) {
            return;
        }
        if (CALLS.size() >= 128) {
            CALLS.removeFirst();
        }
        CALLS.add(new Call(
                side,
                asset,
                stack.getItem(),
                stack.hasFoil(),
                stack.get(DataComponents.DYED_COLOR),
                stack.get(DataComponents.TRIM),
                ModContent.isMixedBoots(stack),
                outlineColor));
    }

    public static synchronized List<Call> snapshot() {
        return List.copyOf(CALLS);
    }

    public record Call(
            MixedBootsEquipmentRenderer.Side side,
            ResourceKey<EquipmentAsset> asset,
            Item item,
            boolean foil,
            DyedItemColor dye,
            ArmorTrim trim,
            boolean mixedStack,
            int outlineColor) {
    }
}
