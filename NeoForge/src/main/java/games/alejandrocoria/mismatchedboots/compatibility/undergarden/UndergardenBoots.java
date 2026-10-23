package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fixed native entitlements; no optional classes or mutable source callbacks. */
public final class UndergardenBoots {
    private static final ResourceKey<Item> CLOGGRUM = key("cloggrum_boots");
    private static final ResourceKey<Item> FROSTSTEEL = key("froststeel_boots");

    private UndergardenBoots() {}

    public static boolean goo(boolean original, ItemStack boots, Holder<Item> item) {
        // Only extend the boot predicate: native Gooey and full-block behavior stay untouched.
        return original || item.is(CLOGGRUM) && MixedBootsSources.anySource(boots, source -> source.is(item));
    }

    public static boolean snow(ItemStack boots) {
        return MixedBootsSources.anySource(boots, source -> source.is(FROSTSTEEL));
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("undergarden", name));
    }
}
