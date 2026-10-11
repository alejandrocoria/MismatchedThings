package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Only an equipped keyboard press can prepare; a reply cannot revive stale client intent. */
public final class JustDireGroundStompInput {
    private static int sequence;
    private static Pending pending;

    private JustDireGroundStompInput() {}

    public static void key(InputEvent.Key event) {
        var client = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || client.player == null || client.screen != null) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        if (!JustDireSettingsCompatibility.keyboardBinding(root, event.getKey())) { return; }
        pending = new Pending(root, snapshot(root), event.getKey(), ++sequence);
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Prepare(sequence, event.getKey(), false));
    }

    public static void reply(JustDireActiveNetwork.ActivationReply reply) {
        if (!reply.prepared() || pending == null || reply.sequence() != pending.sequence) { return; }
        Pending intent = pending; pending = null;
        var client = Minecraft.getInstance();
        if (!reply.accepted() || reply.nonce().equals(JustDireActiveNetwork.NONE) || client.player == null || client.screen != null) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        if (root != intent.root || !ItemStack.matches(root, intent.snapshot)
                || !JustDireSettingsCompatibility.keyboardBinding(root, intent.key)) { return; }
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Activate(intent.sequence, reply.nonce(), intent.key, false));
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (pending != null && (client.player == null || client.screen != null
                || client.player.getItemBySlot(EquipmentSlot.FEET) != pending.root
                || !ItemStack.matches(pending.root, pending.snapshot))) { pending = null; }
    }

    public static void reset() { pending = null; sequence = 0; }

    private static ItemStack snapshot(ItemStack root) {
        var snapshot = root.copy();
        // Native immutable records can be held in mutable lists; detach those from the live root.
        for (String path : List.of("ability_bindings", "ability_cooldowns")) { copyList(snapshot, path); }
        return snapshot;
    }
    @SuppressWarnings("unchecked") private static void copyList(ItemStack root, String path) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).ifPresent(holder -> {
            var type = (DataComponentType<List<Object>>) holder.value();
            if (root.has(type)) { root.set(type, List.copyOf(root.get(type))); }
        });
    }
    private record Pending(ItemStack root, ItemStack snapshot, int key, int sequence) {}
}
