package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;

public class MismatchedBootsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ModContent.MIXED_BOOTS_DATA_ID,
                ModContent.createMixedBootsDataType());
        Registry.register(
                BuiltInRegistries.ITEM,
                ModContent.MIXED_BOOTS_ID,
                new FabricMixedBootsItem(ModContent.mixedBootsProperties().setId(ModContent.MIXED_BOOTS_KEY)));
        var workbench = Registry.register(
                BuiltInRegistries.BLOCK,
                ModContent.MISMATCHED_WORKBENCH_ID,
                ModContent.createMismatchedWorkbenchBlock());
        var workbenchItem = Registry.register(
                BuiltInRegistries.ITEM,
                ModContent.MISMATCHED_WORKBENCH_ID,
                new BlockItem(workbench, ModContent.mismatchedWorkbenchItemProperties()
                        .setId(ModContent.MISMATCHED_WORKBENCH_ITEM_KEY)));
        Registry.register(
                BuiltInRegistries.MENU,
                ModContent.MISMATCHED_WORKBENCH_ID,
                ModContent.createMismatchedWorkbenchMenuType());
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ModContent.SMITHING_BASE_RECIPE_ID,
                ModContent.createSmithingBaseRecipeSerializer());
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ModContent.MIXED_BOOTS_REPAIR_RECIPE_ID,
                ModContent.createMixedBootsRepairRecipeSerializer());
        Registry.register(
                BuiltInRegistries.RECIPE_TYPE,
                ModContent.MISMATCHED_WORKBENCH_ID,
                ModContent.mismatchedWorkbenchRecipeType());
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ModContent.MIXED_BOOTS_WORKBENCH_RECIPE_ID,
                ModContent.createMixedBootsWorkbenchRecipeSerializer());
        RecipeSynchronization.synchronizeRecipeSerializer(ModContent.createMixedBootsWorkbenchRecipeSerializer());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> output.accept(workbenchItem));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            AttributeRulesLifecycle.onServerStarting();
            ComponentRulesLifecycle.onServerStarting();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AttributeRulesLifecycle.onServerStopped();
            ComponentRulesLifecycle.onServerStopped();
        });
        MismatchedBoots.init();
    }
}
