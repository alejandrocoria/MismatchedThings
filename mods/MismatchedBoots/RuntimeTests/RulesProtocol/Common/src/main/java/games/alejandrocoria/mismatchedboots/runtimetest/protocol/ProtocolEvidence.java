package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Passive observer shared with unchanged published peers; deliberately no Boots/Things API references. */
public final class ProtocolEvidence {
    private static int ticks;

    private ProtocolEvidence() {
    }

    public static void write(String name, String text) {
        try {
            Files.createDirectories(directory());
            Files.writeString(directory().resolve(name), text);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void started(MinecraftServer server) {
        write("server-ready.txt", "protocol observer ready");
    }

    public static void tick(MinecraftServer server) {
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            write("server-failure.txt", "Incompatible protocol entered gameplay");
            server.halt(false);
        } else if (Files.exists(directory().resolve("client-protocol-rejected.txt"))) {
            write("server-success.txt", "no gameplay");
            server.halt(false);
        } else if (Files.exists(directory().resolve("client-failure.txt")) || ++ticks > 3_600) {
            write("server-failure.txt", "Client did not prove protocol rejection");
            server.halt(false);
        }
    }

    private static Path directory() {
        return Path.of(System.getProperty("mismatchedboots.runtimetest.markers"));
    }
}
