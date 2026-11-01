package games.alejandrocoria.mismatchedthings.internal.client.viewer.rei;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.client.viewer.rei.WorkbenchReiDisplay;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;

public class WorkbenchReiPlugin implements REIClientPlugin {
    @Override
    @SuppressWarnings("removal")
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new WorkbenchReiCategory());
        registry.addWorkstations(WorkbenchReiDisplay.CATEGORY, EntryStacks.of(MismatchedWorkbench.item()));
        registry.removePlusButton(WorkbenchReiDisplay.CATEGORY);
    }
}
