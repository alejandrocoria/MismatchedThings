package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.network.AttributeRulesPayload;
import games.alejandrocoria.mismatchedboots.network.ComponentRulesPayload;
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

    public static synchronized void recordClientReady(boolean forced) {
        EVENTS.add(new Event("client_ready", forced, null));
    }

    public static synchronized void recordAttributeRules(AttributeRulesPayload payload, boolean forced) {
        EVENTS.add(new Event("attribute_rules", forced, payload));
    }

    public static synchronized void recordComponentRules(ComponentRulesPayload payload, boolean forced) {
        EVENTS.add(new Event("component_rules", forced, payload));
    }

    public static synchronized List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public record Event(String name, boolean forced, CustomPacketPayload payload) {
    }
}
