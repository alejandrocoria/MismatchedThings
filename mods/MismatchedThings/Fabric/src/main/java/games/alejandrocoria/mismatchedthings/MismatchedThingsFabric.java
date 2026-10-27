package games.alejandrocoria.mismatchedthings;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;

public final class MismatchedThingsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        var block = Registry.register(BuiltInRegistries.BLOCK, MismatchedWorkbench.ID, WorkbenchContent.createBlock());
        Registry.register(BuiltInRegistries.ITEM, MismatchedWorkbench.ID, new BlockItem(block, WorkbenchContent.itemProperties()));
        Registry.register(BuiltInRegistries.MENU, MismatchedWorkbench.ID, WorkbenchContent.createMenu());
        Registry.register(BuiltInRegistries.RECIPE_TYPE, MismatchedWorkbench.ID, MismatchedWorkbench.recipeType());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                             .register(output -> output.accept(MismatchedWorkbench.item()));
    }
}
