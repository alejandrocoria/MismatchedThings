package games.alejandrocoria.mismatchedboots.compatibility.allthemodium;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fixed boot entitlements; no optional classes, equipment substitution or source mutation. */
public final class AllTheModiumBoots {
    private static final ResourceKey<Item> ALLTHEMODIUM = key("allthemodium_boots");
    private static final ResourceKey<Item> VIBRANIUM = key("vibranium_boots");
    private static final ResourceKey<Item> UNOBTAINIUM = key("unobtainium_boots");

    private AllTheModiumBoots() {
    }

    public static boolean eligible(ItemStack stack) {
        return MixedBootsSources.anySource(stack,
                                          source -> source.is(ALLTHEMODIUM) || source.is(VIBRANIUM) || source.is(UNOBTAINIUM));
    }

    public static ItemStack recognize(ItemStack original, Player player, EquipmentSlot slot) {
        if (!original.isEmpty() || slot != EquipmentSlot.FEET) {
            return original;
        }

        ItemStack boots = player.getItemBySlot(slot);
        // Native FEET consumers only test emptiness; their damage filters and effects stay upstream.
        return eligible(boots) ? boots : original;
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("allthemodium", name));
    }
}
