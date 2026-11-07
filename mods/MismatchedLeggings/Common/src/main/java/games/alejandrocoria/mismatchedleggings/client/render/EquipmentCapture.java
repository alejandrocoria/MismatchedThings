package games.alejandrocoria.mismatchedleggings.client.render;

import net.minecraft.client.renderer.SubmitNodeCollector;

/** Product-internal, pair-preflight contract; implementations own all deferred render data. */
public interface EquipmentCapture {
    boolean prepareSplit(boolean left);

    boolean prepareFull();

    void flush(SubmitNodeCollector destination);
}
