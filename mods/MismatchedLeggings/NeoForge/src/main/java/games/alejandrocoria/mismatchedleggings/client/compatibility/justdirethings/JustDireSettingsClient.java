package games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/** Native selection/edit queue; ordinary slot packets carry the root. */
public final class JustDireSettingsClient {
    private static JustDireSettingsScreenAccess selected;
    private static ClientLevel world;
    private static int selection;
    private static int request;
    private static int lastReply;
    private static int menu;
    private static int slot;
    private static UUID token = JustDireSettingsNetwork.NONE;
    private static final Queue<Edit> queued = new ArrayDeque<>();

    private JustDireSettingsClient() {
    }

    public static boolean available() {
        return JustDireSettings.available();
    }

    /** Only custom mode is fixed; location and its native presentation remain editable. */
    public static void restrict(Button mode) {
        mode.active = false;
    }

    public static void select(JustDireSettingsScreenAccess screen) {
        deselect();
        if (!screen.mismatchedleggings$jdtReady() || !JustDireSettings.mixed(screen.mismatchedleggings$jdtTool())
                || JustDireSettings.metadataItem(screen.mismatchedleggings$jdtTool()) == null) {
            return;
        }
        selected = screen;
        world = Minecraft.getInstance().level;
        selection++;
        token = JustDireSettingsNetwork.NONE;
        menu = ((AbstractContainerScreen<?>) screen).getMenu().containerId;
        slot = screen.mismatchedleggings$jdtSlot();
        send(new Edit(JustDireSettingsNetwork.SELECT, "", 0, -1, false, true));
    }

    /** True also when unavailable/unacknowledged: a mixed callback must never send a native edit. */
    public static boolean edit(JustDireSettingsScreenAccess screen, String ability, int operation,
                               int value, int key, boolean mouse, boolean equipped) {
        if (!JustDireSettings.mixed(screen.mismatchedleggings$jdtTool())) {
            return false;
        }
        if (screen != selected || !screen.mismatchedleggings$jdtReady() || !JustDireSettings.available()) {
            return true;
        }
        Edit edit = new Edit(operation, ability, value, key, mouse, equipped);
        if (!token.equals(JustDireSettingsNetwork.NONE)) {
            send(edit);
        } else if (queued.size() < 32) {
            queued.add(edit);
        }
        return true;
    }

    private static void send(Edit edit) {
        // The native screen mutates toolSlot before notifying a switch. Deselect the old target,
        // not that new field, or its still-valid server token could survive native-item selection.
        ClientPacketDistributor.sendToServer(new JustDireSettingsNetwork.SettingsRequest(menu,
                                                                                           slot,
                                                                                           selection,
                                                                                           ++request,
                                                                                           token,
                                                                                           edit.operation,
                                                                                           edit.ability,
                                                                                           edit.value,
                                                                                           edit.key,
                                                                                           edit.mouse,
                                                                                           edit.equipped));
    }

    public static void reply(JustDireSettingsNetwork.SettingsReply reply) {
        var client = Minecraft.getInstance();
        if (selected == null || client.screen != selected || reply.selection() != selection
                || ((AbstractContainerScreen<?>) selected).getMenu().containerId != reply.menu()
                || reply.request() <= lastReply) {
            return;
        }
        lastReply = reply.request();
        token = reply.accepted() ? reply.token() : JustDireSettingsNetwork.NONE;
        refreshRoot(client);
        if (selected == null) {
            return;
        }
        if (!reply.accepted()) {
            // A rejected token cannot authorize later edits. Require native reselection instead
            // of leaving active-looking widgets that would queue forever without another SELECT.
            selected.mismatchedleggings$jdtClear();
            deselect();
        } else {
            while (!queued.isEmpty()) {
                send(queued.remove());
            }
        }
        // Do not rebuild native widgets after accepted edits: dragging/capture state must survive.
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (selected == null) {
            return;
        }
        if (client.player == null || client.level != world || client.screen != selected) {
            deselect();
            return;
        }
        refreshRoot(client);
    }

    private static void refreshRoot(Minecraft client) {
        if (client.player == null || selected == null) {
            return;
        }
        var root = client.player.getInventory().getItem(slot);
        if (!selected.mismatchedleggings$jdtReady() || !JustDireSettings.mixed(root)
                || JustDireSettings.metadataItem(root) == null) {
            selected.mismatchedleggings$jdtClear();
            deselect();
            return;
        }
        selected.mismatchedleggings$jdtTool(root);
    }

    private static void deselect() {
        if (selected != null && Minecraft.getInstance().getConnection() != null) {
            // Also cancel a SELECT whose reply has not arrived. Its queued edits must not
            // survive switching to a native item before the server issues the token.
            send(new Edit(JustDireSettingsNetwork.DESELECT, "", 0, -1, false, true));
        }
        selected = null;
        world = null;
        token = JustDireSettingsNetwork.NONE;
        queued.clear();
    }

    public static void reset() {
        selected = null;
        world = null;
        token = JustDireSettingsNetwork.NONE;
        queued.clear();
        selection = 0;
        request = 0;
        lastReply = 0;
    }

    private record Edit(int operation, String ability, int value, int key, boolean mouse, boolean equipped) {
    }
}
