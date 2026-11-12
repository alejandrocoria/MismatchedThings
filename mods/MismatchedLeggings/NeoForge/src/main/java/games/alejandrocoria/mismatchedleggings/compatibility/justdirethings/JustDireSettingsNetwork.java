package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-owned native-menu selections. No client stack, source entitlement or native edit packet is trusted. */
public final class JustDireSettingsNetwork {
    public static final int SELECT = 0;
    public static final int TOGGLE = 1;
    public static final int VALUE = 2;
    public static final int PARTICLES = 3; // Recognized by the UI interceptor, deliberately unsupported.
    public static final int BINDING = 4;
    public static final int DESELECT = 6;
    public static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, State> states = new HashMap<>();
    private static Consumer<SettingsReply> replies = ignored -> {};

    private JustDireSettingsNetwork() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> states.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> states.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> states.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> {
            var state = states.get(event.getEntity().getUUID());
            if (state != null) { state.session = null; }
        });
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SettingsRequest.TYPE, SettingsRequest.CODEC, (request, context) -> {
            var player = (ServerPlayer) context.player();
            var reply = edit(player, request);
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, reply);
        });
        registrar.playToClient(SettingsReply.TYPE, SettingsReply.CODEC, (reply, context) -> replies.accept(reply));
    }

    /** Installed only from client setup; dedicated handlers never link client classes. */
    public static void clientReplies(Consumer<SettingsReply> receiver) { replies = receiver; }

    public static boolean supportedSlot(int slot) {
        return slot >= 0 && slot < Inventory.INVENTORY_SIZE
                || slot == EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE) || slot == Inventory.SLOT_OFFHAND;
    }

    public static SettingsReply edit(ServerPlayer player, SettingsRequest request) {
        var state = states.computeIfAbsent(player.getUUID(), ignored -> new State());
        var menu = player.containerMenu;
        if (request.menu != menu.containerId || !JustDireSettings.nativeMenu(menu)
                || !supportedSlot(request.slot) || request.slot >= player.getInventory().getContainerSize()
                || request.selection <= 0 || request.request <= 0) {
            state.session = null;
            return reply(request, NONE, false);
        }
        if (request.operation == SELECT) {
            if (request.selection <= state.selection || !request.token.equals(NONE)) { return reply(request, NONE, false); }
            state.selection = request.selection;
            state.session = null;
            var root = player.getInventory().getItem(request.slot);
            if (!JustDireSettings.mixed(root) || JustDireSettings.metadataItem(root) == null) { return reply(request, NONE, false); }
            state.session = new Session(menu, root, snapshot(root), request.slot, UUID.randomUUID(), request.request);
            return reply(request, state.session.token, true);
        }

        var session = state.session;
        if (session == null || session.menu != menu || request.selection != state.selection || !session.token.equals(request.token)
                || session.slot != request.slot || player.getInventory().getItem(request.slot) != session.root
                || request.request <= session.lastRequest || !ItemStack.matches(session.snapshot, snapshot(session.root))) {
            state.session = null;
            return reply(request, NONE, false);
        }
        session.lastRequest = request.request; // Even refused requests cannot replay a toggle.
        if (request.operation == DESELECT) {
            state.session = null;
            return reply(request, NONE, true);
        }
        boolean accepted = JustDireSettings.edit(session.root, request);
        if (accepted) { session.snapshot = snapshot(session.root); }
        else { state.session = null; }
        return reply(request, accepted ? session.token : NONE, accepted);
    }

    static ItemStack snapshot(ItemStack root) {
        var copy = root.copy();
        // Charging, ordinary wear and derived equipment projection do not replace the selected pair.
        copy.remove(DataComponents.DAMAGE);
        copy.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        for (String path : List.of("forge_energy", "ability_cooldowns")) {
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path))
                    .ifPresent(holder -> copy.remove(holder.value()));
        }
        DataComponentType<List<Object>> bindings = bindingsType();
        if (bindings != null && copy.has(bindings)) { copy.set(bindings, List.copyOf(copy.get(bindings))); }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<List<Object>> bindingsType() {
        return (DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse("justdirethings:ability_bindings"))
                .map(holder -> holder.value()).orElse(null);
    }

    private static SettingsReply reply(SettingsRequest request, UUID token, boolean accepted) {
        return new SettingsReply(request.menu, request.selection, request.request, token, accepted);
    }

    private static final class State {
        int selection;
        Session session;
    }

    private static final class Session {
        final AbstractContainerMenu menu;
        final ItemStack root;
        final int slot;
        final UUID token;
        ItemStack snapshot;
        int lastRequest;

        Session(AbstractContainerMenu menu, ItemStack root, ItemStack snapshot, int slot, UUID token, int lastRequest) {
            this.menu = menu;
            this.root = root;
            this.snapshot = snapshot;
            this.slot = slot;
            this.token = token;
            this.lastRequest = lastRequest;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "jdt_" + path));
    }

    private static void exhausted(RegistryFriendlyByteBuf buffer) {
        if (buffer.isReadable()) { throw new DecoderException("Trailing mixed leggings JDT settings bytes"); }
    }

    public record SettingsRequest(int menu, int slot, int selection, int request, UUID token, int operation,
                                  String ability, int value, int key, boolean mouse, boolean equipped) implements CustomPacketPayload {
        public static final Type<SettingsRequest> TYPE = JustDireSettingsNetwork.type("settings_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsRequest> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.menu);
            buffer.writeVarInt(packet.slot);
            buffer.writeVarInt(packet.selection);
            buffer.writeVarInt(packet.request);
            buffer.writeUUID(packet.token);
            buffer.writeVarInt(packet.operation);
            buffer.writeUtf(packet.ability, 32);
            buffer.writeVarInt(packet.value);
            buffer.writeVarInt(packet.key);
            buffer.writeBoolean(packet.mouse);
            buffer.writeBoolean(packet.equipped);
        }, buffer -> {
            var packet = new SettingsRequest(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUUID(), buffer.readVarInt(), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean());
            exhausted(buffer);
            return packet;
        });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SettingsReply(int menu, int selection, int request, UUID token, boolean accepted) implements CustomPacketPayload {
        public static final Type<SettingsReply> TYPE = JustDireSettingsNetwork.type("settings_reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsReply> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.menu);
            buffer.writeVarInt(packet.selection);
            buffer.writeVarInt(packet.request);
            buffer.writeUUID(packet.token);
            buffer.writeBoolean(packet.accepted);
        }, buffer -> {
            var packet = new SettingsReply(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean());
            exhausted(buffer);
            return packet;
        });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
