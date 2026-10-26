package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.AdvancedNetheriteCompatibilityAssertions;
import net.fabricmc.api.ModInitializer;

public final class AdvancedNetheriteFabricTestContent implements ModInitializer {
    @Override
    public void onInitialize() {
        AdvancedNetheriteCompatibilityAssertions.registerHookBoot();
    }
}
