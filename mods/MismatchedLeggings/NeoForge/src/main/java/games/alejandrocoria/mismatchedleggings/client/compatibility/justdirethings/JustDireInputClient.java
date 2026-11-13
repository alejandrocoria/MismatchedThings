package games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireHeldToggleCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettingsNetwork;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Native bindings, without borrowing native item identity or adding an in-screen global shortcut. */
public final class JustDireInputClient {
    private static int sequence;
    private static Pending pending;
    private static KeyMapping toggleTool;
    private static boolean lookupFailed;

    private JustDireInputClient() {}

    public static void key(InputEvent.Key event) {
        press(event.getKey(), false, event.getAction());
        held(event.getAction(), mapping -> mapping.matches(new KeyEvent(event.getKey(), event.getScanCode(), event.getModifiers())));
    }

    public static void mouse(InputEvent.MouseButton.Post event) {
        press(event.getButton(), true, event.getAction());
        held(event.getAction(), mapping -> mapping.matchesMouse(new MouseButtonEvent(0, 0,
                new MouseButtonInfo(event.getButton(), event.getModifiers()))));
    }

    private static void press(int key, boolean mouse, int action) {
        var client = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || client.player == null || client.screen != null
                || !JustDireSettings.validBindingInput(key, mouse)) {
            return;
        }
        var targets = new ArrayList<Intent>();
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            if (!JustDireSettingsNetwork.supportedSlot(slot)) { continue; }
            var root = client.player.getInventory().getItem(slot);
            if (!JustDireSettings.boundAbilities(root, key, mouse, slot == JustDireInputNetwork.legsSlot()).isEmpty()) {
                targets.add(new Intent(slot, JustDireInputNetwork.snapshot(root)));
            }
        }
        if (targets.isEmpty()) { return; }
        pending = new Pending(client.player, client.level, targets, key, mouse, ++sequence);
        ClientPacketDistributor.sendToServer(new JustDireInputNetwork.InputRequest(sequence, key, mouse, JustDireSettingsNetwork.NONE, List.of()));
    }

    public static void reply(JustDireInputNetwork.InputReply reply) {
        if (pending == null || reply.sequence() != pending.sequence) { return; }
        tick();
        var intent = pending;
        pending = null;
        if (intent == null || reply.token().equals(JustDireSettingsNetwork.NONE)) { return; }
        var slots = intent.targets.stream().map(Intent::slot).filter(reply.slots()::contains).toList();
        if (!slots.isEmpty()) {
            ClientPacketDistributor.sendToServer(new JustDireInputNetwork.InputRequest(intent.sequence, intent.key, intent.mouse, reply.token(), slots));
        }
    }

    public static void tick() {
        if (pending == null) { return; }
        var client = Minecraft.getInstance();
        if (client.player != pending.player || client.level != pending.level || client.screen != null) {
            pending = null;
            return;
        }
        pending.targets.removeIf(target -> {
            var root = client.player.getInventory().getItem(target.slot);
            return !ItemStack.matches(target.snapshot, JustDireInputNetwork.snapshot(root))
                    || JustDireSettings.boundAbilities(root, pending.key, pending.mouse, target.slot == JustDireInputNetwork.legsSlot()).isEmpty();
        });
        if (pending.targets.isEmpty()) { pending = null; }
    }

    private static void held(int action, Predicate<KeyMapping> matches) {
        var client = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || client.player == null || client.screen != null) { return; }
        var hand = JustDireHeldToggleCompatibility.target(client.player);
        if (hand == null) { return; } // Optional JDT absence stays quiet.
        var mapping = toggleMapping();
        // The native conflict context rejects mixed identity; retain its actual key/modifier instead.
        if (mapping != null && matches.test(mapping) && mapping.getKeyModifier().isActive(KeyConflictContext.IN_GAME)) {
            ClientPacketDistributor.sendToServer(new JustDireInputNetwork.HeldToggle(hand));
        }
    }

    public static KeyMapping toggleMapping() {
        if (lookupFailed || !JustDireSettings.available()) { return null; }
        if (toggleTool == null) {
            try {
                toggleTool = (KeyMapping) Class.forName("com.direwolf20.justdirethings.client.KeyBindings").getField("toggleTool").get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                lookupFailed = true;
                LeggingsContent.LOG.warn("Could not bind Just Dire Things Toggle Tool key; mixed held shortcut disabled", failure);
            }
        }
        return toggleTool;
    }

    public static void reset() {
        pending = null;
        sequence = 0;
    }

    private record Intent(int slot, ItemStack snapshot) {}
    private record Pending(LocalPlayer player, ClientLevel level, List<Intent> targets, int key, boolean mouse, int sequence) {}
}
