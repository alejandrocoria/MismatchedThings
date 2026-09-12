package games.alejandrocoria.mismatchedboots.content;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsRecipe;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.Optional;

public final class ModContent {
    public static final String MIXED_BOOTS_NAME = "mixed_boots";
    public static final String MIXED_BOOTS_DATA_NAME = "mixed_boots_data";
    public static final String MIXED_BOOTS_RECIPE_NAME = "mixed_boots";
    public static final Identifier MIXED_BOOTS_ID = id(MIXED_BOOTS_NAME);
    public static final Identifier MIXED_BOOTS_DATA_ID = id(MIXED_BOOTS_DATA_NAME);
    public static final Identifier MIXED_BOOTS_RECIPE_ID = id(MIXED_BOOTS_RECIPE_NAME);
    public static final ResourceKey<Item> MIXED_BOOTS_KEY = ResourceKey.create(Registries.ITEM, MIXED_BOOTS_ID);

    private ModContent() {
    }

    public static Item.Properties mixedBootsProperties() {
        return new Item.Properties().stacksTo(1).equippable(EquipmentSlot.FEET);
    }

    public static DataComponentType<MixedBootsData> createMixedBootsDataType() {
        return DataComponentType.<MixedBootsData>builder()
                .persistent(MixedBootsData.CODEC)
                .networkSynchronized(MixedBootsData.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    public static RecipeSerializer<MixedBootsRecipe> createMixedBootsRecipeSerializer() {
        return MixedBootsRecipe.SERIALIZER;
    }

    @SuppressWarnings("unchecked")
    public static Optional<DataComponentType<MixedBootsData>> mixedBootsDataType() {
        return Optional.ofNullable((DataComponentType<MixedBootsData>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(MIXED_BOOTS_DATA_ID));
    }

    public static Optional<MixedBootsData> getMixedBootsData(ItemStack stack) {
        Optional<MixedBootsData> registeredData = mixedBootsDataType().map(stack::get);
        if (registeredData.isPresent()) {
            return registeredData;
        }
        for (TypedDataComponent<?> component : stack.getComponents()) {
            if (component.value() instanceof MixedBootsData data) {
                return Optional.of(data);
            }
        }
        return Optional.empty();
    }

    public static boolean isMixedBoots(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof MixedBootsItem
                || MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                || getMixedBootsData(stack).isPresent());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
