package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.MismatchedWorkbenchBlock;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

final class ModRegistries {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Constants.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Constants.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Constants.MOD_ID);
    private static final RegistryObject<MismatchedWorkbenchBlock> MISMATCHED_WORKBENCH =
            BLOCKS.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::createMismatchedWorkbenchBlock);
    private static final RegistryObject<BlockItem> MISMATCHED_WORKBENCH_ITEM = ITEMS.register(
            ModContent.MISMATCHED_WORKBENCH_NAME,
            () -> new BlockItem(MISMATCHED_WORKBENCH.get(), ModContent.mismatchedWorkbenchItemProperties()
                    .setId(ModContent.MISMATCHED_WORKBENCH_ITEM_KEY)));

    static {
        ITEMS.register(
                ModContent.MIXED_BOOTS_NAME,
                () -> new MixedBootsItem(ModContent.mixedBootsProperties().setId(ModContent.MIXED_BOOTS_KEY)));
        MENU_TYPES.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::createMismatchedWorkbenchMenuType);
        DATA_COMPONENT_TYPES.register(ModContent.MIXED_BOOTS_DATA_NAME, ModContent::createMixedBootsDataType);
        RECIPE_TYPES.register(ModContent.MISMATCHED_WORKBENCH_NAME, ModContent::mismatchedWorkbenchRecipeType);
        RECIPE_SERIALIZERS.register(ModContent.SMITHING_BASE_RECIPE_NAME, ModContent::createSmithingBaseRecipeSerializer);
        RECIPE_SERIALIZERS.register(
                ModContent.MIXED_BOOTS_WORKBENCH_RECIPE_NAME,
                ModContent::createMixedBootsWorkbenchRecipeSerializer);
    }

    private ModRegistries() {
    }

    static void register(BusGroup modBusGroup) {
        DATA_COMPONENT_TYPES.register(modBusGroup);
        BLOCKS.register(modBusGroup);
        ITEMS.register(modBusGroup);
        MENU_TYPES.register(modBusGroup);
        RECIPE_TYPES.register(modBusGroup);
        RECIPE_SERIALIZERS.register(modBusGroup);
        BuildCreativeModeTabContentsEvent.BUS.addListener(ModRegistries::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(MISMATCHED_WORKBENCH_ITEM.get());
        }
    }
}
