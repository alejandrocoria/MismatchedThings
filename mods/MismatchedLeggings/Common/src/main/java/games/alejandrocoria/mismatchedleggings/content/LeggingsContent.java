package games.alejandrocoria.mismatchedleggings.content;

import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public final class LeggingsContent {
    public static final String MOD_ID = "mismatchedleggings";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier MIXED_LEGGINGS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "mixed_leggings");
    public static final Identifier MIXED_LEGGINGS_DATA_ID = Identifier.fromNamespaceAndPath(MOD_ID, "mixed_leggings_data");
    // Reserved for block K. No recovery component/schema is registered until preservation exists.
    public static final Identifier SUSPENDED_LEGGINGS_ID = Identifier.fromNamespaceAndPath(MOD_ID, "suspended_leggings");

    private LeggingsContent() {
    }

    public static DataComponentType<MixedLeggingsData> createDataType() {
        return DataComponentType.<MixedLeggingsData>builder().persistent(MixedLeggingsData.CODEC)
                .networkSynchronized(MixedLeggingsData.STREAM_CODEC).cacheEncoding().build();
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<MixedLeggingsData> mixedLeggingsDataType() {
        return (DataComponentType<MixedLeggingsData>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(MIXED_LEGGINGS_DATA_ID);
    }

    public static Item createItem() {
        return createItem(MixedLeggingsItem::new);
    }

    public static Item createItem(Function<Item.Properties, ? extends MixedLeggingsItem> factory) {
        // Bare roots have no equipment component; only later validated composition may add one.
        return factory.apply(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MIXED_LEGGINGS_ID))
                .stacksTo(1));
    }
}
