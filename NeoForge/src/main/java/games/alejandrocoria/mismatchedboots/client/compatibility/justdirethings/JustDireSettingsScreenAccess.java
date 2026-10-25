package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import net.minecraft.world.item.ItemStack;

/** Access only to the actual native selection, never a projected inventory/tool identity. */
public interface JustDireSettingsScreenAccess {
    ItemStack mismatchedboots$jdtTool();

    int mismatchedboots$jdtSlot();

    boolean mismatchedboots$jdtReady();

    void mismatchedboots$jdtTool(ItemStack root);

    void mismatchedboots$jdtClear();
}
