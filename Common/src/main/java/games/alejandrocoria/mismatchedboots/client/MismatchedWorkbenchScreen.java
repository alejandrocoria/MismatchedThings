package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class MismatchedWorkbenchScreen extends AbstractContainerScreen<MismatchedWorkbenchMenu> {
    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier ARROWS_SPRITE = Identifier.fromNamespaceAndPath(
            Constants.MOD_ID, "container/mismatched_workbench/arrows");
    private static final int BACKGROUND_COLOR = 0xFFC6C6C6;
    private static final int DISABLED_COLOR = 0x80666666;

    public MismatchedWorkbenchScreen(
            MismatchedWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 183);
        this.inventoryLabelY = 89;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE,
                left, top, 0.0F, 0.0F, 176, 83, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE,
                left, top + 83, 0.0F, 82.0F, 176, 17, 176, 1, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE,
                left, top + 100, 0.0F, 83.0F, 176, 83, 256, 256);
        graphics.fill(left + 7, top + 7, left + 169, top + 100, BACKGROUND_COLOR);

        for (int index = MismatchedWorkbenchMenu.INPUT_START;
                index < MismatchedWorkbenchMenu.RESULT_END;
                index++) {
            Slot slot = this.menu.getSlot(index);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    SLOT_SPRITE, left + slot.x - 1, top + slot.y - 1, 18, 18);
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                ARROWS_SPRITE, left + 75, top + 39, 26, 26);

        if (this.menu.isCommitted()) {
            for (int index = MismatchedWorkbenchMenu.INPUT_START;
                    index < MismatchedWorkbenchMenu.INPUT_END;
                    index++) {
                Slot slot = this.menu.getSlot(index);
                graphics.fill(left + slot.x, top + slot.y,
                        left + slot.x + 16, top + slot.y + 16, DISABLED_COLOR);
            }
        }
    }

}
