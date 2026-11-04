package games.alejandrocoria.rulesspike;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Proof {
    public static final boolean CANDIDATE = Boolean.getBoolean("rules.spike.candidate");
    public static final String CASE = System.getProperty("rules.spike.case", "new-new");
    public static final String REASON = "Mismatched Boots network protocol differs. Use compatible Boots versions on client and server.";
    private static int serverTicks;

    private Proof() {
    }

    public static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }

    public static void write(String name, String text) {
        try {
            Path directory = Path.of(System.getProperty("rules.spike.reports"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), text);
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void started(MinecraftServer server) {
        write("server-ready.txt", "ready");
        if (CASE.equals("seed")) {
            server.halt(false);
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (++serverTicks > 3600 || Files.exists(Path.of(System.getProperty("rules.spike.reports"), "client-ok.txt"))
                || Files.exists(Path.of(System.getProperty("rules.spike.reports"), "client-failure.txt"))) {
            server.halt(false);
        }
    }
}
