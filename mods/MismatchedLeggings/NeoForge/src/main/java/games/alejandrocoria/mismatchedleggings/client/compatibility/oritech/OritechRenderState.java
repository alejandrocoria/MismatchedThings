package games.alejandrocoria.mismatchedleggings.client.compatibility.oritech;

/** Optional client state attachment, cleared/replaced for every native extraction. */
public interface OritechRenderState {
    OritechEquipmentCapture.Frame mismatchedleggings$exoFrame();

    void mismatchedleggings$exoFrame(OritechEquipmentCapture.Frame frame);
}
