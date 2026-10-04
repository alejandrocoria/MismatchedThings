package games.alejandrocoria.mismatchedboots.platform;

import games.alejandrocoria.mismatchedboots.platform.services.IPlatformHelper;

import java.nio.file.Path;

public final class CommonTestPlatformHelper implements IPlatformHelper {
    @Override
    public Path getConfigDirectory() {
        return Path.of("config");
    }

    @Override
    public String getPlatformName() {
        return "Common tests";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return true;
    }
}
