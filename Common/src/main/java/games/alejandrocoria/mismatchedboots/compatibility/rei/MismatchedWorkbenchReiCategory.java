package games.alejandrocoria.mismatchedboots.compatibility.rei;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

final class MismatchedWorkbenchReiCategory implements DisplayCategory<MismatchedWorkbenchReiDisplay> {
    private static final Identifier ARROWS_TEXTURE = Identifier.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/gui/sprites/container/mismatched_workbench/arrows.png");
    private static final int WIDTH = 130;
    private static final int HEIGHT = 72;
    private static final int SLOT_SPACING = 22;
    private static final int VERTICAL_PADDING = 5;
    private static final int SLOT_SIZE = 18;
    private static final int ARROWS_SIZE = 26;

    @Override
    public CategoryIdentifier<? extends MismatchedWorkbenchReiDisplay> getCategoryIdentifier() {
        return MismatchedWorkbenchReiDisplay.CATEGORY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.mismatchedboots.mismatched_workbench");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModContent.mismatchedWorkbenchItem().orElseThrow());
    }

    @Override
    public List<Widget> setupDisplay(MismatchedWorkbenchReiDisplay display, Rectangle bounds) {
        int pairWidth = SLOT_SPACING + SLOT_SIZE;
        // REI treats a slot Point as the item origin and draws its frame one pixel to the left.
        int leftSlotX = bounds.getCenterX() - pairWidth / 2 + 1;
        int rightSlotX = leftSlotX + SLOT_SPACING;
        int inputY = bounds.y + VERTICAL_PADDING;
        int arrowsY = inputY + SLOT_SIZE - 1;
        int outputY = inputY + SLOT_SIZE + ARROWS_SIZE;

        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        // Input/output semantics live in the display. Marking parallel slot lists makes REI pin
        // one focused value into unrelated slots instead of preserving their shared index.
        widgets.add(Widgets.createSlot(new Point(leftSlotX, inputY))
                .entries(display.getInputEntries().get(0)));
        widgets.add(Widgets.createSlot(new Point(rightSlotX, inputY))
                .entries(display.getInputEntries().get(1)));
        widgets.add(Widgets.createTexturedWidget(
                ARROWS_TEXTURE,
                new Rectangle(bounds.getCenterX() - ARROWS_SIZE / 2, arrowsY, ARROWS_SIZE, ARROWS_SIZE),
                0,
                0,
                ARROWS_SIZE,
                ARROWS_SIZE));
        widgets.add(Widgets.createSlot(new Point(leftSlotX, outputY))
                .entries(display.getOutputEntries().get(0)));
        widgets.add(Widgets.createSlot(new Point(rightSlotX, outputY))
                .entries(display.getOutputEntries().get(1)));
        return List.copyOf(widgets);
    }

    @Override
    public int getDisplayWidth(MismatchedWorkbenchReiDisplay display) {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }
}
