package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireHeldToggleCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/** Native selection/edit queue and held global shortcut; ordinary slot packets carry the root. */
public final class JustDireSettingsClient {
    private static JustDireSettingsScreenAccess selected;
    private static int selection, request, lastReply, menu, slot;
    private static UUID token = JustDireActiveNetwork.NONE;
    private static final Queue<Edit> queued = new ArrayDeque<>();
    private static KeyMapping toggleTool;
    private static boolean toggleLookupFailed;

    private JustDireSettingsClient() {}

    public static boolean available() { return JustDireSettingsCompatibility.available(); }

    /** Only custom mode is fixed; location and its native presentation remain editable. */
    public static void restrict(Button mode) {
        mode.active = false;
    }

    public static void select(JustDireSettingsScreenAccess screen) {
        deselect();
        if (!screen.mismatchedboots$jdtReady() || !JustDireSettingsCompatibility.mixed(screen.mismatchedboots$jdtTool())
                || JustDireSettingsCompatibility.metadataItem(screen.mismatchedboots$jdtTool()) == null) { return; }
        selected = screen; selection++; token = JustDireActiveNetwork.NONE;
        menu = ((AbstractContainerScreen<?>) screen).getMenu().containerId;
        slot = screen.mismatchedboots$jdtSlot();
        send(new Edit(JustDireActiveNetwork.SELECT, "", 0, -1, false, true));
    }

    /** True also when unavailable/unacknowledged: a mixed callback must never send a native edit. */
    public static boolean edit(JustDireSettingsScreenAccess screen, String ability, int operation,
                               int value, int key, boolean mouse, boolean equipped) {
        if (!JustDireSettingsCompatibility.mixed(screen.mismatchedboots$jdtTool())) { return false; }
        if (screen != selected || !screen.mismatchedboots$jdtReady() || !JustDireSettingsCompatibility.available()) { return true; }
        Edit edit = new Edit(operation, ability, value, key, mouse, equipped);
        if (!token.equals(JustDireActiveNetwork.NONE)) { send(edit); }
        else if (queued.size() < 32) { queued.add(edit); }
        return true;
    }

    private static void send(Edit edit) {
        // The native screen mutates toolSlot before notifying a switch. Deselect the old target,
        // not that new field, or its still-valid server token could survive native-item selection.
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.SettingsRequest(menu,
                slot, selection, ++request, token, edit.operation,
                edit.ability, edit.value, edit.key, edit.mouse, edit.equipped));
    }

    public static void reply(JustDireActiveNetwork.SettingsReply reply) {
        var client = Minecraft.getInstance();
        if (selected == null || client.screen != selected || reply.selection() != selection
                || ((AbstractContainerScreen<?>) selected).getMenu().containerId != reply.menu()
                || reply.request() <= lastReply) { return; }
        lastReply = reply.request();
        token = reply.accepted() ? reply.token() : JustDireActiveNetwork.NONE;
        refreshRoot(client);
        if (selected == null) { return; }
        if (!reply.accepted()) {
            // A rejected token cannot authorize later edits. Require native reselection instead
            // of leaving active-looking widgets that would queue forever without another SELECT.
            selected.mismatchedboots$jdtClear(); deselect();
        } else {
            while (!queued.isEmpty()) { send(queued.remove()); }
        }
        // Do not rebuild native widgets after accepted edits: dragging/capture state must survive.
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (selected == null) { return; }
        if (client.player == null || client.screen != selected) { deselect(); return; }
        refreshRoot(client);
    }

    private static void refreshRoot(Minecraft client) {
        if (client.player == null || selected == null) { return; }
        var root = client.player.getInventory().getItem(slot);
        if (!selected.mismatchedboots$jdtReady() || !JustDireSettingsCompatibility.mixed(root)
                || JustDireSettingsCompatibility.metadataItem(root) == null) {
            selected.mismatchedboots$jdtClear(); deselect(); return;
        }
        selected.mismatchedboots$jdtTool(root);
    }

    private static void deselect() {
        if (selected != null && !token.equals(JustDireActiveNetwork.NONE) && Minecraft.getInstance().getConnection() != null) {
            send(new Edit(JustDireActiveNetwork.DESELECT, "", 0, -1, false, true));
        }
        selected = null; token = JustDireActiveNetwork.NONE; queued.clear();
    }

    public static void heldKey(InputEvent.Key event) {
        heldToggle(event.getAction(), mapping -> mapping.matches(new KeyEvent(event.getKey(), event.getScanCode(), event.getModifiers())));
    }

    public static void heldMouse(InputEvent.MouseButton.Post event) {
        heldToggle(event.getAction(), mapping -> mapping.matchesMouse(new MouseButtonEvent(0, 0,
                new MouseButtonInfo(event.getButton(), event.getModifiers()))));
    }

    private static void heldToggle(int action, java.util.function.Predicate<KeyMapping> matches) {
        var client = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || client.player == null || client.screen != null) { return; }
        var hand = JustDireHeldToggleCompatibility.target(client.player);
        if (hand == null) { return; } // Also keeps absent JDT quiet and leaves every native click untouched.
        KeyMapping mapping = toggleMapping();
        // Native conflict context rejects mixed identity; match its configured key/modifier directly.
        if (mapping == null || !matches.test(mapping) || !mapping.getKeyModifier().isActive(KeyConflictContext.IN_GAME)) { return; }
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.HeldToggle(hand));
    }

    static KeyMapping toggleMapping() {
        if (toggleLookupFailed) { return null; }
        if (toggleTool == null) {
            try {
                toggleTool = (KeyMapping) Class.forName("com.direwolf20.justdirethings.client.KeyBindings")
                        .getField("toggleTool").get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                toggleLookupFailed = true;
                Constants.LOG.warn("Could not bind Just Dire Things settings toggle key; mixed global shortcut disabled", failure);
                return null;
            }
        }
        return toggleTool;
    }

    public static void reset() {
        selected = null; token = JustDireActiveNetwork.NONE; queued.clear(); selection = request = lastReply = 0;
    }

    private record Edit(int operation, String ability, int value, int key, boolean mouse, boolean equipped) {}
}
