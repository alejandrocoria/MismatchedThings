package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test-only selected-root session, not a production protocol or a native-handler replacement. */
public final class JustDireSettingsSpikeNetwork {
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static long nextToken;
    private JustDireSettingsSpikeNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("settings-spike-1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            var player = (ServerPlayer) context.player();
            try {
                boolean accepted = apply(player, request);
                player.containerMenu.broadcastChanges();
                var session = sessions.get(player.getUUID());
                context.reply(new Reply(request.menu, request.selection, request.request,
                        accepted && session != null ? session.token : 0, accepted));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned binding API changed", failure); }
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC, (reply, context) -> JustDireSettingsSpikeClient.reply(reply));
    }

    private static boolean apply(ServerPlayer player, Request request) throws ReflectiveOperationException {
        if (!System.getProperty("mismatchedboots.runtimetest.justDireSettingsVariant", "live").equals("live")) {
            sessions.remove(player.getUUID());
            return false;
        }
        var menu = player.containerMenu;
        if (menu.containerId != request.menu || !menu.getClass().getName().equals(
                "com.direwolf20.justdirethings.common.containers.ToolSettingContainer")) { return false; }
        if (request.operation == 0) {
            sessions.remove(player.getUUID());
            if (request.slot != 36 || request.selection <= 0 || request.token != 0) { return false; }
            var root = player.getInventory().getItem(request.slot);
            if (JustDireSettingsSpikeSupport.view(root) == null) { return false; }
            sessions.put(player.getUUID(), new Session(menu, root, request.selection, ++nextToken));
            return true;
        }
        var session = sessions.get(player.getUUID());
        if (session == null || session.menu != menu || request.slot != 36 || session.selection != request.selection
                || session.token != request.token || player.getInventory().getItem(36) != session.root
                || !request.ability.equals("groundstomp") || request.request <= session.lastRequest) { return false; }
        // Consume the request number even on rejection; a duplicated toggle must not toggle twice.
        session.lastRequest = request.request;
        var root = session.root;
        var view = JustDireSettingsSpikeSupport.view(root);
        if (view == null || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_upgrade_installed"), false)) {
            return false;
        }
        switch (request.operation) {
            case 1 -> {
                var type = JustDireAttributeTestSupport.<Boolean>component("groundstomp_toggle");
                root.set(type, !root.getOrDefault(type, true));
            }
            case 2 -> {
                if (request.value < JustDireSettingsSpikeSupport.parameter(view, "minSlider")
                        || request.value > JustDireSettingsSpikeSupport.parameter(view, "maxSlider")) { return false; }
                JustDireAttributeTestSupport.set(root, "groundstomp_value", request.value);
            }
            case 3 -> {
                if (request.value != 0 && request.value != 1) { return false; }
                JustDireAttributeTestSupport.set(root, "groundstomp_custom_setting", request.value);
            }
            case 4 -> {
                if ((request.value != 0 && request.value != 2) || request.mouse || !request.equipped
                        || (request.key != -1 && (request.key < 32 || request.key > 348))) { return false; }
                var bindingType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
                var bindings = new ArrayList<>(root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of()));
                bindings.removeIf(binding -> {
                    try { return bindingType.getMethod("abilityName").invoke(binding).equals("groundstomp"); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                });
                if (request.value == 2 && request.key != -1) {
                    bindings.add(bindingType.getConstructor(String.class, int.class, boolean.class, boolean.class)
                            .newInstance("groundstomp", request.key, false, true));
                }
                JustDireAttributeTestSupport.set(root, "ability_bindings", List.copyOf(bindings));
                JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", request.value);
            }
            default -> { return false; }
        }
        return true;
    }

    private static final class Session {
        final AbstractContainerMenu menu;
        final ItemStack root;
        final int selection;
        final long token;
        int lastRequest;
        Session(AbstractContainerMenu menu, ItemStack root, int selection, long token) {
            this.menu = menu; this.root = root; this.selection = selection; this.token = token;
        }
    }

    public record Request(int menu, int slot, int selection, int request, long token, int operation,
                          String ability, int value, int key, boolean mouse, boolean equipped) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "settings_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buffer, request) -> {
            buffer.writeVarInt(request.menu); buffer.writeVarInt(request.slot); buffer.writeVarInt(request.selection);
            buffer.writeVarInt(request.request); buffer.writeLong(request.token); buffer.writeVarInt(request.operation);
            buffer.writeUtf(request.ability, 32); buffer.writeVarInt(request.value); buffer.writeVarInt(request.key);
            buffer.writeBoolean(request.mouse); buffer.writeBoolean(request.equipped);
        }, buffer -> {
            var result = new Request(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean());
            if (buffer.isReadable()) { throw new DecoderException("Trailing settings request bytes"); }
            return result;
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Reply(int menu, int selection, int request, long token, boolean accepted) implements CustomPacketPayload {
        static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "settings_reply"));
        static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of((buffer, reply) -> {
            buffer.writeVarInt(reply.menu); buffer.writeVarInt(reply.selection); buffer.writeVarInt(reply.request);
            buffer.writeLong(reply.token); buffer.writeBoolean(reply.accepted);
        }, buffer -> new Reply(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
