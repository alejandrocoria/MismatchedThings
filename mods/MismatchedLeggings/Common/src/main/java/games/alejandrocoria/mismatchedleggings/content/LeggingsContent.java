package games.alejandrocoria.mismatchedleggings.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class LeggingsContent {
    public static final String MOD_ID = "mismatchedleggings";
    public static final Identifier MIXED_LEGGINGS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "mixed_leggings");

    private LeggingsContent() {
    }

    public static Item createItem() {
        // Bare roots have no equipment component; only later validated composition may add one.
        return new MixedLeggingsItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MIXED_LEGGINGS_ID))
                .stacksTo(1));
    }
}
