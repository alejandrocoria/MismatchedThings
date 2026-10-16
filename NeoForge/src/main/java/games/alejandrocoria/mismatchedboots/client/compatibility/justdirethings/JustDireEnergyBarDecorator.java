package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.transfer.access.ItemAccess;

/** Adds live root FE without replacing vanilla durability or decorating either source view. */
public final class JustDireEnergyBarDecorator implements IItemDecorator {
    public static final JustDireEnergyBarDecorator INSTANCE = new JustDireEnergyBarDecorator();
    private boolean failed;

    private JustDireEnergyBarDecorator() {}

    @Override
    public boolean render(GuiGraphicsExtractor graphics, Font font, ItemStack root, int x, int y) {
        if (failed || !ModContent.isMixedBoots(root)) { return false; }
        int width;
        int offset;
        try {
            var receiver = JustDireResourcesCompatibility.energyBarReceiver(root);
            if (receiver == null) { return false; }
            var storage = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
            if (storage == null || storage.getCapacityAsLong() <= 0 || !receiver.isBarVisible(root)) { return false; }
            // Native methods query the passed root's capability, even for a lower-tier receiver.
            width = receiver.getBarWidth(root);
            if (width < 0 || width > 13) { throw new IllegalStateException("Unsupported native FE bar width: " + width); }
            // Visibility includes Unbreakable; damage alone would leave an unnecessary upper row.
            offset = root.isBarVisible() ? 10 : 13;
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            Constants.LOG.warn("Just Dire Things energy bar query failed; FE decoration disabled", failure);
            return false;
        }
        graphics.fill(x + 2, y + offset, x + 15, y + offset + 2, 0xFF000000);
        graphics.fill(x + 2, y + offset, x + 2 + width, y + offset + 1, 0xFF40CFFF);
        return true;
    }
}
