package games.alejandrocoria.mismatchedthings.spike.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("mismatchedthings")
public final class CoreNeoForge {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WorkbenchCore.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WorkbenchCore.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, WorkbenchCore.NAMESPACE);
    private static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, WorkbenchCore.NAMESPACE);

    public CoreNeoForge(IEventBus bus) {
        WorkbenchCore.initializations++;
        var block = BLOCKS.register("mismatched_workbench", WorkbenchCore::createBlock);
        ITEMS.registerSimpleBlockItem(block, WorkbenchCore::itemProperties);
        MENUS.register("mismatched_workbench", WorkbenchCore::createMenu);
        RECIPES.register("mismatched_workbench", () -> WorkbenchRecipe.TYPE);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        RECIPES.register(bus);
        bus.addListener(CoreNeoForgeClient::screens);
    }
}
