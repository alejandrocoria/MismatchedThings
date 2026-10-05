package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

final class GameTestContexts {
    private GameTestContexts() {
    }

    // Broad runs omit context-only scenarios; their XML report explicitly marks them skipped.
    // A selected scenario must never quietly pass without its dedicated fixture.
    static boolean requireOrSkip(GameTestHelper helper, String requiredProperty, boolean enabled) {
        if (enabled) {
            return true;
        }
        helper.assertTrue(System.getProperty("fabric-api.gametest.filter") == null,
                "Focused GameTest is missing its required context: " + requiredProperty);
        helper.succeed();
        return false;
    }
}
