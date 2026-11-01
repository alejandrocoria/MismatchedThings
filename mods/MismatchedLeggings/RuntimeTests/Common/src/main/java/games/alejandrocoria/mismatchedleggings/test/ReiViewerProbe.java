package games.alejandrocoria.mismatchedleggings.test;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ReiViewerProbe implements ViewerProbe {
    @Override
    public boolean ready() {
        return !PluginManager.areAnyReloading();
    }

    @Override
    public List<Cycle> cycles(String namespace) {
        return search(namespace).streamDisplays().map(spec -> spec.provideInternalDisplay())
                .map(display -> new Cycle(stacks(display.getInputEntries().get(0)), stacks(display.getInputEntries().get(1)),
                        stacks(display.getOutputEntries().get(0)), stacks(display.getOutputEntries().get(1))))
                .filter(cycle -> namespace.equals("mismatchedthings") || BuiltInRegistries.ITEM.getKey(cycle.primary().getFirst().getItem()).getNamespace().equals(namespace))
                .toList();
    }

    @Override
    public void open(String namespace) {
        search(namespace).open();
    }

    @Override
    public void verifyCategories(boolean legacyBoots) {
        var registry = CategoryRegistry.getInstance();
        if (registry.tryGet(CategoryIdentifier.of("mismatchedthings", "mismatched_workbench")).isEmpty()
                || registry.tryGet(CategoryIdentifier.of("mismatchedleggings", "mismatched_workbench")).isPresent()
                || registry.tryGet(CategoryIdentifier.of("mismatchedboots", "mismatched_workbench")).isPresent() != legacyBoots) {
            throw new AssertionError("Unexpected shared/legacy REI categories");
        }
    }

    private static ViewSearchBuilder search(String namespace) {
        String owner = namespace.equals("mismatchedboots") && Boolean.getBoolean("leggings.test.previousBoots")
                ? namespace : "mismatchedthings";
        var category = CategoryIdentifier.of(owner, "mismatched_workbench");
        return ViewSearchBuilder.builder().addCategory(category).filterCategory(category).mergingDisplays(false);
    }

    private static List<ItemStack> stacks(EntryIngredient entries) {
        return entries.stream().map(entry -> entry.<ItemStack>castValue().copy()).toList();
    }
}
