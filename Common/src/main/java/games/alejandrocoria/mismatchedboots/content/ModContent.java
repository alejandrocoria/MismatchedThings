package games.alejandrocoria.mismatchedboots.content;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsRepairRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsSmithingBaseRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.Optional;

public final class ModContent {
    public static final String MIXED_BOOTS_NAME = "mixed_boots";
    public static final String MIXED_BOOTS_DATA_NAME = "mixed_boots_data";
    public static final String SMITHING_BASE_RECIPE_NAME = "smithing_base";
    public static final String MISMATCHED_WORKBENCH_NAME = "mismatched_workbench";
    public static final String MIXED_BOOTS_REPAIR_RECIPE_NAME = "mixed_boots_repair";
    public static final String MIXED_BOOTS_WORKBENCH_RECIPE_NAME = "mixed_boots_workbench";
    public static final Identifier MIXED_BOOTS_ID = id(MIXED_BOOTS_NAME);
    public static final Identifier MIXED_BOOTS_DATA_ID = id(MIXED_BOOTS_DATA_NAME);
    public static final Identifier SMITHING_BASE_RECIPE_ID = id(SMITHING_BASE_RECIPE_NAME);
    public static final Identifier MISMATCHED_WORKBENCH_ID = id(MISMATCHED_WORKBENCH_NAME);
    public static final Identifier MIXED_BOOTS_REPAIR_RECIPE_ID = id(MIXED_BOOTS_REPAIR_RECIPE_NAME);
    public static final Identifier MIXED_BOOTS_WORKBENCH_RECIPE_ID = id(MIXED_BOOTS_WORKBENCH_RECIPE_NAME);
    public static final ResourceKey<Item> MIXED_BOOTS_KEY = ResourceKey.create(Registries.ITEM, MIXED_BOOTS_ID);
    public static final ResourceKey<Item> MISMATCHED_WORKBENCH_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, MISMATCHED_WORKBENCH_ID);
    public static final ResourceKey<Block> MISMATCHED_WORKBENCH_KEY =
            ResourceKey.create(Registries.BLOCK, MISMATCHED_WORKBENCH_ID);

    private ModContent() {
    }

    public static Item.Properties mixedBootsProperties() {
        // Only valid composition outputs project EQUIPPABLE; a bare registry shell is inert.
        return new Item.Properties().stacksTo(1);
    }

    public static DataComponentType<MixedBootsData> createMixedBootsDataType() {
        return DataComponentType.<MixedBootsData>builder()
                .persistent(MixedBootsData.CODEC)
                .networkSynchronized(MixedBootsData.STREAM_CODEC)
                .cacheEncoding()
                .build();
    }

    public static RecipeSerializer<MixedBootsSmithingBaseRecipe> createSmithingBaseRecipeSerializer() {
        return MixedBootsSmithingBaseRecipe.SERIALIZER;
    }

    public static RecipeType<MismatchedWorkbenchRecipe> mismatchedWorkbenchRecipeType() {
        return MismatchedWorkbenchRecipe.TYPE;
    }

    public static RecipeSerializer<MixedBootsRepairRecipe> createMixedBootsRepairRecipeSerializer() {
        return MixedBootsRepairRecipe.SERIALIZER;
    }

    public static RecipeSerializer<MixedBootsWorkbenchRecipe> createMixedBootsWorkbenchRecipeSerializer() {
        return MixedBootsWorkbenchRecipe.SERIALIZER;
    }

    public static MismatchedWorkbenchBlock createMismatchedWorkbenchBlock() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .ignitedByLava()
                .setId(MISMATCHED_WORKBENCH_KEY);
        return new MismatchedWorkbenchBlock(properties);
    }

    public static Item.Properties mismatchedWorkbenchItemProperties() {
        return new Item.Properties().useBlockDescriptionPrefix();
    }

    public static MenuType<MismatchedWorkbenchMenu> createMismatchedWorkbenchMenuType() {
        return new MenuType<>(MismatchedWorkbenchMenu::new, FeatureFlags.VANILLA_SET);
    }

    public static Optional<Block> mismatchedWorkbenchBlock() {
        return Optional.ofNullable(BuiltInRegistries.BLOCK.getValue(MISMATCHED_WORKBENCH_ID));
    }

    public static Optional<Item> mismatchedWorkbenchItem() {
        return Optional.ofNullable(BuiltInRegistries.ITEM.getValue(MISMATCHED_WORKBENCH_ID));
    }

    @SuppressWarnings("unchecked")
    public static Optional<MenuType<MismatchedWorkbenchMenu>> mismatchedWorkbenchMenuType() {
        return Optional.ofNullable((MenuType<MismatchedWorkbenchMenu>)
                BuiltInRegistries.MENU.getValue(MISMATCHED_WORKBENCH_ID));
    }

    @SuppressWarnings("unchecked")
    public static Optional<DataComponentType<MixedBootsData>> mixedBootsDataType() {
        return Optional.ofNullable((DataComponentType<MixedBootsData>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(MIXED_BOOTS_DATA_ID));
    }

    /** Broad data discovery, including unregistered component types used by fixtures.
     * Not proof of a valid gameplay item: consumers must validate item identity and sources.
     */
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

    /** Broad classification for excluding mixed/nested sources, not gameplay admission. */
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
