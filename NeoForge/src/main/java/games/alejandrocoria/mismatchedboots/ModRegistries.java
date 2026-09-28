package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class ModRegistries {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Constants.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Constants.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Constants.MOD_ID);

    static {
        ITEMS.registerItem(ModContent.MIXED_BOOTS_NAME, NeoForgeMixedBootsItem::new, ModContent::mixedBootsProperties);
        BLOCKS.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::createMismatchedWorkbenchBlock);
        MENU_TYPES.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::createMismatchedWorkbenchMenuType);
        DATA_COMPONENT_TYPES.register(ModContent.MIXED_BOOTS_DATA_NAME, ModContent::createMixedBootsDataType);
        RECIPE_TYPES.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::mismatchedWorkbenchRecipeType);
        RECIPE_SERIALIZERS.register(ModContent.MIXED_BOOTS_RECIPE_NAME, ModContent::createMixedBootsRecipeSerializer);
        RECIPE_SERIALIZERS.register(ModContent.SMITHING_BASE_RECIPE_NAME, ModContent::createSmithingBaseRecipeSerializer);
        RECIPE_SERIALIZERS.register(
                ModContent.MIXED_BOOTS_WORKBENCH_RECIPE_NAME,
                ModContent::createMixedBootsWorkbenchRecipeSerializer);
    }

    private ModRegistries() {
    }

    static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        MENU_TYPES.register(eventBus);
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
