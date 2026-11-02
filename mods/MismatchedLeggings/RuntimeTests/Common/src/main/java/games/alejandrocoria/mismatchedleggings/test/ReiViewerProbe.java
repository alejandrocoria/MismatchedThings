package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.api.client.viewer.rei.WorkbenchReiDisplay;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

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
    public void verifyOutputTooltips(String namespace) {
        if (namespace.equals("mismatchedboots") && Boolean.getBoolean("leggings.test.previousBoots")) {
            // Published Boots owns its old separator implementation, not the Things marker under test.
            return;
        }
        var client = Minecraft.getInstance();
        var category = CategoryRegistry.getInstance().get(WorkbenchReiDisplay.CATEGORY).getCategory();
        int verified = 0;
        for (var spec : search(namespace).streamDisplays().toList()) {
            var display = (WorkbenchReiDisplay) spec.provideInternalDisplay();
            var output = display.getOutputEntries().getFirst().getFirst().<ItemStack>castValue();
            if (!BuiltInRegistries.ITEM.getKey(output.getItem()).getNamespace().equals(namespace)) {
                continue;
            }
            var bounds = new Rectangle(24, 24, category.getDisplayWidth(display), category.getDisplayHeight());
            var slots = category.setupDisplay(display, bounds).stream().filter(Slot.class::isInstance).map(Slot.class::cast).toList();
            require(slots.size() == 4, "REI category lost its two input and two output slots");
            for (var slot : slots.subList(2, 4)) {
                var point = new Point(slot.getInnerBounds().getCenterX(), slot.getInnerBounds().getCenterY());
                require(slot.containsMouse(point), "Output tooltip probe did not hover the slot");
                var tooltip = slot.getCurrentTooltip(TooltipContext.of(point, Item.TooltipContext.of(client.level), TooltipFlag.NORMAL));
                require(tooltip != null && tooltip.entries().stream().filter(entry -> entry.isText()
                                && entry.getAsText() == TooltipSeparator.INSTANCE).count() == 2,
                        "REI output tooltip lost the two source boundaries");
                var state = new GuiRenderState();
                var graphics = new GuiGraphics(client, state, point.x, point.y);
                var overlay = REIRuntime.getInstance().getOverlay().orElseThrow();
                try {
                    // Only API classes are compile dependencies; invoke the installed platform renderer.
                    overlay.getClass().getMethod("renderTooltip", GuiGraphics.class, Tooltip.class).invoke(overlay, graphics, tooltip);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("REI output tooltip rendering failed for " + namespace, failure);
                }
                graphics.renderDeferredElements();
                int[] textRows = {0};
                state.forEachText(row -> textRows[0]++);
                require(textRows[0] > 0, "REI skipped extracting the hovered tooltip");
                require(tooltip.entries().stream().noneMatch(entry -> entry.isTooltipComponent()
                                && entry.getAsTooltipComponent() == (Object) TooltipSeparator.INSTANCE),
                        "REI classified a source boundary twice and would append duplicate lines");
                verified++;
            }
        }
        require(verified == 2, "REI did not render both output tooltips for " + namespace);
        System.out.println("REI output tooltips rendered: " + namespace + " primary/inverse");
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
