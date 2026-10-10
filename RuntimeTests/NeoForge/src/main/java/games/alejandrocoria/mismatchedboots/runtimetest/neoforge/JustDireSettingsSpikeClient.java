package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Native widget callbacks use a mixed-only channel. All native originals retain their own channel. */
public final class JustDireSettingsSpikeClient {
    static Object selectedScreen;
    static int selection;
    static int request;
    static long token;
    static JustDireSettingsSpikeNetwork.Reply lastReply;

    private JustDireSettingsSpikeClient() {}

    public static void select(Object screen) {
        selectedScreen = screen; selection++; token = 0;
        var root = (ItemStack) field(screen, "tool");
        send(screen, JustDireSettingsSpikeSupport.mixed(root) ? (int) field(screen, "toolSlot") : -1, 0, "", 0, -1, false, true);
    }

    public static boolean edit(Object screen, String name, int operation, int value, int key, boolean mouse, boolean equipped) {
        if (!JustDireSettingsSpikeSupport.mixed((ItemStack) field(screen, "tool"))) { return false; }
        if (selectedScreen == screen && token != 0) {
            send(screen, (int) field(screen, "toolSlot"), operation, name, value, key, mouse, equipped);
        }
        return true; // No native packet for an unacknowledged mixed selection.
    }

    static JustDireSettingsSpikeNetwork.Request packet(Object screen, int operation, String name, int value, int key,
                                                       boolean mouse, boolean equipped) {
        return new JustDireSettingsSpikeNetwork.Request(((AbstractContainerScreen<?>) screen).getMenu().containerId,
                (int) field(screen, "toolSlot"), selection, ++request, token, operation, name, value, key, mouse, equipped);
    }

    private static void send(Object screen, int slot, int operation, String name, int value, int key, boolean mouse, boolean equipped) {
        var packet = new JustDireSettingsSpikeNetwork.Request(((AbstractContainerScreen<?>) screen).getMenu().containerId,
                slot, selection, ++request, token, operation, name, value, key, mouse, equipped);
        ClientPacketDistributor.sendToServer(packet);
    }

    static void reply(JustDireSettingsSpikeNetwork.Reply reply) {
        lastReply = reply;
        var screen = Minecraft.getInstance().screen;
        if (screen == null || screen != selectedScreen || reply.selection() != selection
                || ((AbstractContainerScreen<?>) screen).getMenu().containerId != reply.menu()) { return; }
        if (reply.accepted()) { token = reply.token(); }
        var root = (ItemStack) field(screen, "tool");
        if (!JustDireSettingsSpikeSupport.mixed(root)) { return; }
        // Container slot packets precede the reply. Refresh the selected stack, not the widget tree
        // on every accepted slider edit: rebuilding while dragging loses native interaction state.
        setField(screen, "tool", Minecraft.getInstance().player.getInventory().getItem((int) field(screen, "toolSlot")));
        if (!reply.accepted()) { invoke(screen, "refreshButtons"); }
    }

    public static Object field(Object owner, String name) {
        try { return reflectedField(owner, name).get(owner); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static void setField(Object owner, String name, Object value) {
        try { reflectedField(owner, name).set(owner, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static java.lang.reflect.Field reflectedField(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static void invoke(Object owner, String name) {
        try { owner.getClass().getMethod(name).invoke(owner); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
