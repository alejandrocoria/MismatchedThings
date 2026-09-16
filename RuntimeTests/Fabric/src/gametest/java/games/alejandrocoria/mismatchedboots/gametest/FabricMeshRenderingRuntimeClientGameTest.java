package games.alejandrocoria.mismatchedboots.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Repeats the complete world/submission/reload matrix using mesh-only source layers. */
public final class FabricMeshRenderingRuntimeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        new HybridItemRenderingRuntimeClientGameTest(true).runTest(context);
    }
}
