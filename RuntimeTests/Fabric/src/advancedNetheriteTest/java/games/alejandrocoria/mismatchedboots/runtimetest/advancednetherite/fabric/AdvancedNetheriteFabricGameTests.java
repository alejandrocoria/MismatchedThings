package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.AdvancedNetheriteCompatibilityAssertions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public final class AdvancedNetheriteFabricGameTests {
    private static final String EMPTY_STRUCTURE =
            AdvancedNetheriteCompatibilityAssertions.TEST_NAMESPACE + ":empty";

    @GameTest(structure = EMPTY_STRUCTURE)
    public void exactArtifactMatrixAndHookDispatchPass(GameTestHelper helper) {
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        AdvancedNetheriteCompatibilityAssertions.assertCompleteMatrix(player);
        helper.succeed();
    }
}
