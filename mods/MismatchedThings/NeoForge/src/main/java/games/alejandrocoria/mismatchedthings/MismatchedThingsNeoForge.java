package games.alejandrocoria.mismatchedthings;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.client.MismatchedThingsClientNeoForge;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MismatchedWorkbench.MOD_ID)
public final class MismatchedThingsNeoForge {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MismatchedWorkbench.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MismatchedWorkbench.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MismatchedWorkbench.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, MismatchedWorkbench.MOD_ID);

    public MismatchedThingsNeoForge(IEventBus bus) {
        var block = BLOCKS.register("mismatched_workbench", WorkbenchContent::createBlock);
        ITEMS.registerSimpleBlockItem(block, WorkbenchContent::itemProperties);
        MENUS.register("mismatched_workbench", WorkbenchContent::createMenu);
        RECIPES.register("mismatched_workbench", MismatchedWorkbench::recipeType);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        RECIPES.register(bus);

        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(MismatchedThingsClientNeoForge::registerScreens);
        }
    }
}
