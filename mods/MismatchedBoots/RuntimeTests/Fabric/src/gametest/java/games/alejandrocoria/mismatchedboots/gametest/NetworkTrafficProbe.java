package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public final class NetworkTrafficProbe {
    private static final List<Event> EVENTS = new ArrayList<>();

    private NetworkTrafficProbe() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
    }

    public static synchronized void recordClientReady() {
        EVENTS.add(new Event("client_ready", null));
    }

    public static synchronized void recordRules(CompositionRulesPayload payload) {
        EVENTS.add(new Event("rules", payload));
    }

    public static synchronized List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public record Event(String name, CustomPacketPayload payload) {
    }
}
