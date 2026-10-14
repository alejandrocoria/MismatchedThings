package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Historical research driver; shares the maintained production gameplay assertions. */
public final class NeoForgeJustDirePoweredJumpTest {
    private static boolean finished;
    private NeoForgeJustDirePoweredJumpTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeJustDireJumpTest::observeFall);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; } finished = true;
        String variant = System.getProperty("mismatchedboots.runtimetest.justDirePoweredJumpVariant", "live");
        try {
            JustDirePoweredJumpSpike.bind();
            var report = new StringBuilder();
            int cases;
            if (variant.equals("live")) { cases = NeoForgeJustDireJumpTest.poweredMatrix(server, report); }
            else {
                NeoForgeJustDireJumpTest.check(server, NeoForgeJustDireJumpTest.poweredBoots("eclipsealloy", 5), 5);
                var root = NeoForgeJustDireJumpTest.poweredPair("eclipsealloy", "iron");
                NeoForgeJustDireJumpTest.charge(root, 5);
                NeoForgeJustDireJumpTest.check(server, root, variant.equals("control") ? 5 : 0);
                root = NeoForgeJustDireJumpTest.poweredPair("eclipsealloy", "ferricore");
                NeoForgeJustDireJumpTest.charge(root, 5);
                NeoForgeJustDireJumpTest.check(server, root, variant.equals("control") ? 5 : 0);
                if (JustDirePoweredJumpSpike.queries != 0) { throw new AssertionError("Normal archive or denied paired hooks used the candidate"); }
                cases = 3;
            }
            NetworkAcceptanceFiles.write("just-dire-powered-jump-report.txt", cases + " native/mixed movement and real-health cases; variant="
                    + variant + "; queries=" + JustDirePoweredJumpSpike.queries + "\n" + report
                    + "historical research driver; production acceptance uses verifyJustDireJump without a research copy\n");
            NetworkAcceptanceFiles.write("just-dire-powered-jump-success.txt", variant + "\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-powered-jump", failure); }
        finally { server.halt(false); }
    }
}
