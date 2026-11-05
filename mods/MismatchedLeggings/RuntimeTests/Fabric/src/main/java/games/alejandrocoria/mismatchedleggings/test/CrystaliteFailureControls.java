package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Fixture-only fault injection; production has no testing switches or replacement dispatch. */
public final class CrystaliteFailureControls {
    public static boolean dropProbeCallback;
    public static boolean failRightSplit;
    public static int probeAttempts;
    public static int preparedLeft;
    public static int rejectedRight;

    private CrystaliteFailureControls() {
    }

    public static SubmitNodeCollector probeCollector(SubmitNodeCollector destination) {
        if (!dropProbeCallback) {
            return destination;
        }
        probeAttempts++;
        return new SubmitNodeStorage() {
            private int callbacks;

            @Override
            public SubmitNodeCollection order(int order) {
                return new SubmitNodeCollection(this) {
                    @Override
                    public void submitCustomGeometry(PoseStack pose, RenderType type,
                                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
                        if (++callbacks != 6) {
                            destination.order(order).submitCustomGeometry(pose, type, renderer);
                        }
                    }
                };
            }
        };
    }
}
