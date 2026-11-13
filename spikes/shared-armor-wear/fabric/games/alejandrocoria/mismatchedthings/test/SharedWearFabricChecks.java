package games.alejandrocoria.mismatchedthings.test;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Native metadata selection, not a Fabric JDT gameplay port. */
public final class SharedWearFabricChecks {
    private static boolean finished;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getTickCount() < 20) { return; }
            finished = true;
            try {
                var loader = FabricLoader.getInstance();
                var boots = loader.getModContainer("mismatchedboots");
                String graph = System.getProperty("sharedwear.graph");
                boolean legacyAlone = graph.equals("legacy-alone");
                boolean expected = graph.equals("both") || graph.equals("boots") || legacyAlone;
                String bootsVersion = legacyAlone ? "1.0.0" : "1.1.0";
                if (boots.isPresent() != expected || boots.isPresent() && !boots.get().getMetadata().getVersion().getFriendlyString().equals(bootsVersion)) {
                    throw new AssertionError("Unexpected optional Boots selection");
                }
                if (loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedthings")).count() != 1) {
                    throw new AssertionError("Expected one selected core");
                }
                String coreVersion = loader.getModContainer("mismatchedthings").orElseThrow().getMetadata().getVersion().getFriendlyString();
                if (!coreVersion.equals(legacyAlone ? "1.0.0" : "1.1.0")) {
                    throw new AssertionError("Unexpected selected Things: " + coreVersion);
                }
                Files.writeString(Path.of(System.getProperty("sharedwear.report"), "shared-wear.ok"), "native metadata accepted; Things=" + coreVersion + "; Boots=" + bootsVersion + '\n');
            } catch (Throwable failure) {
                try { Files.writeString(Path.of(System.getProperty("sharedwear.report"), "shared-wear.failure.txt"), failure.toString()); }
                catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
                failure.printStackTrace();
            } finally { server.halt(false); }
        });
    }
}
