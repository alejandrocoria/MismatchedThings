package games.alejandrocoria.mismatchedthings.spike.core;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

public final class CoreFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        WorkbenchCore.initializations++;
        var block = Registry.register(BuiltInRegistries.BLOCK, WorkbenchCore.MISMATCHED_WORKBENCH_ID, WorkbenchCore.createBlock());
        Registry.register(BuiltInRegistries.ITEM, WorkbenchCore.MISMATCHED_WORKBENCH_ID, new BlockItem(block, WorkbenchCore.itemProperties()));
        Registry.register(BuiltInRegistries.MENU, WorkbenchCore.MISMATCHED_WORKBENCH_ID, WorkbenchCore.createMenu());
        Registry.register(BuiltInRegistries.RECIPE_TYPE, WorkbenchCore.MISMATCHED_WORKBENCH_ID, WorkbenchRecipe.TYPE);
    }
}
