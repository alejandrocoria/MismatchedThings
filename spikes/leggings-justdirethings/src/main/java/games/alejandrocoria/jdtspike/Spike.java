package games.alejandrocoria.jdtspike;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod("leggings_jdt_spike")
public final class Spike {
    private boolean completed;

    public Spike(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(Candidate::attributes);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Pre event) -> Candidate.movement(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (!completed && event.getServer().isDedicatedServer() && event.getServer().getTickCount() > 20) {
                completed = true;
                try {
                    var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(event.getServer().overworld());
                    Checks.movement(player, false);
                    Checks.phase(player);
                    event.getServer().saveEverything(true, true, true);
                    result("server", null);
                } catch (Throwable failure) {
                    result("server", failure);
                }
                event.getServer().halt(false);
            }
        });
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(ClientProbe::setup);
        }
    }

    public static void result(String side, Throwable failure) {
        try {
            Path directory = Path.of(System.getProperty("jdt.spike.report"));
            Files.createDirectories(directory);
            if (failure == null) {
                Files.writeString(directory.resolve(side + ".ok"), "JDT bounded feasibility checks passed\n");
            } else {
                var trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(directory.resolve(side + ".failure.txt"), trace.toString());
                failure.printStackTrace();
            }
        } catch (java.io.IOException failureToReport) {
            throw new IllegalStateException(failureToReport);
        }
    }
}
