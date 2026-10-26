package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAbilityTarget;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveState;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** One gameplay press, one bounded batch; replies only narrow the original per-slot intent. */
public final class JustDireAbilityInput {
    private static int sequence;
    private static Pending pending;

    private JustDireAbilityInput() {
    }

    public static void key(InputEvent.Key event) {
        press(event.getKey(), false, event.getAction());
    }

    public static void mouse(InputEvent.MouseButton.Post event) {
        press(event.getButton(), true, event.getAction());
    }

    private static void press(int key, boolean mouse, int action) {
        var client = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || client.player == null || client.screen != null) {
            return;
        }

        var targets = new ArrayList<Intent>();
        for (int slot = 0; slot <= 40; slot++) {
            if (!JustDireAbilityTarget.supportedSlot(slot)) {
                continue;
            }

            var root = client.player.getInventory().getItem(slot);
            boolean equipped = slot == JustDireAbilityTarget.FEET_SLOT;
            if (!JustDireSettingsCompatibility.inputBinding(root, key, mouse, equipped)) {
                continue;
            }

            int actions = 0;
            if (JustDireSettingsCompatibility.groundStompBinding(root, key, mouse, equipped)
                    && JustDireGroundStompCompatibility.quote(root) != null) {
                actions |= JustDireActiveState.GROUND_STOMP;
            }
            if (JustDireSettingsCompatibility.earthquakeBinding(root, key, mouse, equipped)
                    && JustDireEarthquakeCompatibility.quote(root) != null) {
                actions |= JustDireActiveState.EARTHQUAKE;
            }
            if (actions == 0 && JustDireSettingsCompatibility.boundPassives(root, key, mouse, equipped).isEmpty()) {
                continue;
            }
            targets.add(new Intent(slot, snapshot(root, actions), actions));
        }
        if (targets.isEmpty()) {
            return;
        }
        pending = new Pending(client.player, client.level, targets, key, mouse, ++sequence);
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Prepare(sequence, key, mouse));
    }

    public static void reply(JustDireActiveNetwork.ActivationReply reply) {
        if (!reply.prepared() || pending == null || reply.sequence() != pending.sequence) {
            return;
        }
        prune();
        Pending intent = pending;
        pending = null;
        if (intent == null || !reply.accepted() || reply.nonce().equals(JustDireActiveNetwork.NONE)) {
            return;
        }
        // Availability may differ at prepare (depleted/moved server slot). Intersect, never add
        // server candidates the client did not capture, or revive one it already discarded.
        var slots = intent.targets.stream().map(Intent::slot).filter(reply.slots()::contains).toList();
        if (slots.isEmpty()) {
            return;
        }
        // No client stacks/action masks are authority. This subset removes server-admitted slots only.
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Activate(intent.sequence, reply.nonce(),
                                                                                    intent.key, intent.mouse, slots));
    }

    public static void tick() {
        prune();
    }

    public static void reset() {
        pending = null;
        sequence = 0;
    }

    private static void prune() {
        var client = Minecraft.getInstance();
        if (pending == null) {
            return;
        }
        if (client.player != pending.player || client.level != pending.level || client.screen != null) {
            pending = null;
            return;
        }
        pending.targets.removeIf(intent -> {
            var root = client.player.getInventory().getItem(intent.slot);
            boolean equipped = intent.slot == JustDireAbilityTarget.FEET_SLOT;
            // Ordinary slot synchronization recreates objects; identity remains server-owned.
            boolean matches = intent.actions == 0 ? ItemStack.matches(snapshot(root, 0), intent.snapshot)
                                                  : equipped ? JustDireActiveState.paidMatches(root, intent.snapshot, intent.actions, 41)
                                                             : ItemStack.matches(root, intent.snapshot);
            return !matches || !JustDireSettingsCompatibility.inputBinding(root, pending.key, pending.mouse, equipped);
        });
        if (pending.targets.isEmpty()) {
            pending = null;
        }
    }

    private static ItemStack snapshot(ItemStack root, int actions) {
        // Server independently decides whether an eligible paid action requires its stricter snapshot.
        if (actions == 0) {
            return JustDireActiveNetwork.passiveInputSnapshot(root);
        }
        return JustDireActiveState.snapshot(root);
    }

    private record Intent(int slot, ItemStack snapshot, int actions) {
    }

    private record Pending(LocalPlayer player, ClientLevel level, List<Intent> targets, int key, boolean mouse, int sequence) {
    }
}
