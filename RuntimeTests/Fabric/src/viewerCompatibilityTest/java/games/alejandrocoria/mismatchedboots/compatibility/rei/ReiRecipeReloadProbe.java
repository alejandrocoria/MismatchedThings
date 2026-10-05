package games.alejandrocoria.mismatchedboots.compatibility.rei;

import games.alejandrocoria.mismatchedboots.runtimetest.viewer.RecipeViewerReloadClientGameTest;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ReiRecipeReloadProbe implements RecipeViewerReloadClientGameTest.Probe {
    @Override
    public boolean ready() {
        return !PluginManager.areAnyReloading();
    }

    @Override
    public List<RecipeViewerReloadClientGameTest.Cycle> cycles() {
        return search().streamDisplays().map(spec -> spec.provideInternalDisplay())
                .map(display -> new RecipeViewerReloadClientGameTest.Cycle(
                        stacks(display.getInputEntries().get(0)), stacks(display.getInputEntries().get(1)),
                        stacks(display.getOutputEntries().get(0)), stacks(display.getOutputEntries().get(1)))).toList();
    }

    @Override
    public void open() {
        search().open();
    }

    private static ViewSearchBuilder search() {
        return ViewSearchBuilder.builder().addCategory(MismatchedWorkbenchReiDisplay.CATEGORY)
                .filterCategory(MismatchedWorkbenchReiDisplay.CATEGORY).mergingDisplays(false);
    }

    private static List<ItemStack> stacks(EntryIngredient entries) {
        return entries.stream().map(entry -> entry.<ItemStack>castValue().copy()).toList();
    }
}
