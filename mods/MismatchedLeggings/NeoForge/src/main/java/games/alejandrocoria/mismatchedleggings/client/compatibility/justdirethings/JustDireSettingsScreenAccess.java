package games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings;

import net.minecraft.world.item.ItemStack;

/** Access only to the actual native selection, never a projected inventory/tool identity. */
public interface JustDireSettingsScreenAccess {
    ItemStack mismatchedleggings$jdtTool();

    int mismatchedleggings$jdtSlot();

    boolean mismatchedleggings$jdtReady();

    void mismatchedleggings$jdtTool(ItemStack root);

    void mismatchedleggings$jdtClear();
}
