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
        if (!JustDireSettingsCompatibility.inputBinding(root, key, mouse)) { return; }
        boolean passive = !JustDireSettingsCompatibility.boundPassives(root, key, mouse).isEmpty();
        pending = new Pending(root, snapshot(root, passive), key, mouse, ++sequence, passive);
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Prepare(sequence, key, mouse));
    }

    public static void reply(JustDireActiveNetwork.ActivationReply reply) {
        if (!reply.prepared() || pending == null || reply.sequence() != pending.sequence) { return; }
        Pending intent = pending; pending = null;
        var client = Minecraft.getInstance();
        if (!reply.accepted() || reply.nonce().equals(JustDireActiveNetwork.NONE) || client.player == null || client.screen != null) { return; }
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        if (root != intent.root || !ItemStack.matches(snapshot(root, intent.passive), intent.snapshot)
                || !JustDireSettingsCompatibility.inputBinding(root, intent.key, intent.mouse)) { return; }
        Network.getNetworkHandler().sendToServer(new JustDireActiveNetwork.Activate(intent.sequence, reply.nonce(), intent.key, intent.mouse));
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (pending != null && (client.player == null || client.screen != null
                || client.player.getItemBySlot(EquipmentSlot.FEET) != pending.root
                || !ItemStack.matches(snapshot(pending.root, pending.passive), pending.snapshot))) { pending = null; }
    }

    public static void reset() { pending = null; sequence = 0; }

    private static ItemStack snapshot(ItemStack root, boolean passive) {
        // Server independently decides whether an eligible paid action requires its stricter snapshot.
        if (passive) { return JustDireActiveNetwork.passiveInputSnapshot(root); }
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
    private record Pending(ItemStack root, ItemStack snapshot, int key, boolean mouse, int sequence, boolean passive) {}
}
