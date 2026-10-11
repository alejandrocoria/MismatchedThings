package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Equipped keyboard-only, opt-in input experiment. Native original items are ignored. */
final class JustDireGroundStompSpikeInput {
    static int sequence;
    static JustDireGroundStompSpikeNetwork.Prepared lastPrepared;
    static JustDireGroundStompSpikeNetwork.Activate lastActivation;
    static JustDireGroundStompSpikeNetwork.Result result;
    static boolean deferActivation; // Hold the genuine prepared packet to model delayed delivery in the fixture.
    private static ItemStack expected;
    private static int key;

    private JustDireGroundStompSpikeInput() {}

    static void key(InputEvent.Key event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || event.getAction() != GLFW.GLFW_PRESS) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        try {
            if (!JustDireGroundStompSpikeNetwork.binding(root, event.getKey(), false)) { return; }
            expected = root.copy(); key = event.getKey();
            lastPrepared = null; lastActivation = null; result = null;
            ClientPacketDistributor.sendToServer(new JustDireGroundStompSpikeNetwork.Prepare(++sequence, key, false));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned binding API changed", failure); }
    }

    static void prepared(JustDireGroundStompSpikeNetwork.Prepared packet) {
        lastPrepared = packet;
        var client = Minecraft.getInstance();
        if (packet.sequence() != sequence) { return; }
        var snapshot = expected; expected = null;
        if (packet.nonce().equals(JustDireGroundStompSpikeNetwork.NONE)
                || client.player == null || client.screen != null || snapshot == null
                || !ItemStack.matches(snapshot, client.player.getItemBySlot(EquipmentSlot.FEET))) { return; }
        lastActivation = new JustDireGroundStompSpikeNetwork.Activate(sequence, packet.nonce(), key, false);
        if (!deferActivation) { ClientPacketDistributor.sendToServer(lastActivation); }
    }
}
