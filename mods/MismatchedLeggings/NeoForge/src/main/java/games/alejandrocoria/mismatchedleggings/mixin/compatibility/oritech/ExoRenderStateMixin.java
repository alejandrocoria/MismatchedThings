package games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech;

import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
abstract class ExoRenderStateMixin implements OritechRenderState {
    @Unique
    private OritechEquipmentCapture.Frame mismatchedleggings$frame;

    @Override
    public OritechEquipmentCapture.Frame mismatchedleggings$exoFrame() {
        return mismatchedleggings$frame;
    }

    @Override
    public void mismatchedleggings$exoFrame(OritechEquipmentCapture.Frame frame) {
        mismatchedleggings$frame = frame;
    }
}
