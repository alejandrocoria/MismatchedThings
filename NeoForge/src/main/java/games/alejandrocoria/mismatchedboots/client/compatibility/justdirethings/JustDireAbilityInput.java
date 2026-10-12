package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveState;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Equipped keyboard/additional-mouse presses only; a reply cannot revive stale client intent. */
public final class JustDireAbilityInput {
    private static int sequence;
    private static Pending pending;

    private JustDireAbilityInput() {}

    public static void key(InputEvent.Key event) {
        press(event.getKey(), false, event.getAction());
    }

    public static void mouse(InputEvent.MouseButton.Post event) {
        // Native mouse routing stops at inventory slot 35, so it cannot reach worn mixed boots.
        press(event.getButton(), true, event.getAction());
    }

    private static void press(int key, boolean mouse, int action) {
        var client = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || client.player == null || client.screen != null) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        // B's server route is ready, but C still owns native Earthquake UI/input. Retained shared
        // bindings must not accidentally activate Earthquake through an ordinary Ground Stomp press.
        if (JustDireSettingsCompatibility.earthquakeBinding(root, key, mouse)) { return; }
        if (!JustDireSettingsCompatibility.inputBinding(root, key, mouse)) { return; }
        boolean passive = !JustDireSettingsCompatibility.boundPassives(root, key, mouse).isEmpty()
                && !(JustDireSettingsCompatibility.groundStompBinding(root, key, mouse)
                && JustDireGroundStompCompatibility.quote(root) != null);
        pending = new Pending(snapshot(root, passive), key, mouse, ++sequence, passive);
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Prepare(sequence, key, mouse));
    }

    public static void reply(JustDireActiveNetwork.ActivationReply reply) {
        if (!reply.prepared() || pending == null || reply.sequence() != pending.sequence) { return; }
        Pending intent = pending; pending = null;
        var client = Minecraft.getInstance();
        if (!reply.accepted() || reply.nonce().equals(JustDireActiveNetwork.NONE) || client.player == null || client.screen != null) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        // Ordinary slot packets recreate the client stack. Paid intent follows only legal peer-clock
        // progression; free switches keep their settings-only rule. Server root identity stays exact.
        if (!matches(root, intent.snapshot, intent.passive)
                || !JustDireSettingsCompatibility.inputBinding(root, intent.key, intent.mouse)) { return; }
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Activate(intent.sequence, reply.nonce(), intent.key, intent.mouse));
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (pending != null && (client.player == null || client.screen != null
                || !matches(client.player.getItemBySlot(EquipmentSlot.FEET), pending.snapshot, pending.passive))) { pending = null; }
    }

    public static void reset() { pending = null; sequence = 0; }

    private static boolean matches(ItemStack root, ItemStack expected, boolean passive) {
        // Ordinary slot packets recreate the client object; only the server can attest the live root identity.
        return passive ? ItemStack.matches(snapshot(root, true), expected)
                : JustDireActiveState.paidMatches(root, expected, JustDireActiveState.GROUND_STOMP, 41);
    }

    private static ItemStack snapshot(ItemStack root, boolean passive) {
        // Server independently decides whether an eligible paid action requires its stricter snapshot.
        if (passive) { return JustDireActiveNetwork.passiveInputSnapshot(root); }
        return JustDireActiveState.snapshot(root);
    }
    private record Pending(ItemStack snapshot, int key, boolean mouse, int sequence, boolean passive) {}
}
