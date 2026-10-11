package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import commonnetwork.api.Network;
import commonnetwork.networking.data.Side;
import games.alejandrocoria.mismatchedboots.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Separate menu edit sessions and consumed-once equipped action permits; server thread only. */
public final class JustDireActiveNetwork {
    public static final int SELECT = 0, TOGGLE = 1, VALUE = 2, PARTICLES = 3, BINDING = 4, TOOL_TOGGLE = 5, DESELECT = 6;
    public static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, State> states = new HashMap<>();
    private static Consumer<ActivationReply> activationReplies = ignored -> {};
    private static Consumer<SettingsReply> settingsReplies = ignored -> {};

    private JustDireActiveNetwork() {}

    public static void initialize() {
        // These mod-owned routes register quietly without JDT. Optional lookup never grants access.
        Network.registerPacket(Prepare.TYPE, Prepare.class, Prepare.CODEC, context -> {
            if (context.side() != Side.SERVER || context.sender() == null) { return; }
            var player = context.sender();
            Network.getNetworkHandler().sendToClient(prepare(player, context.message()), player);
        });
        Network.registerPacket(Activate.TYPE, Activate.class, Activate.CODEC, context -> {
            if (context.side() != Side.SERVER || context.sender() == null) { return; }
            var player = context.sender();
            boolean accepted = activate(player, context.message());
            player.inventoryMenu.broadcastChanges();
            Network.getNetworkHandler().sendToClient(new ActivationReply(context.message().sequence(), context.message().nonce(), false, accepted), player);
        });
        Network.registerPacket(ActivationReply.TYPE, ActivationReply.class, ActivationReply.CODEC, context -> {
            if (context.side() == Side.CLIENT) { activationReplies.accept(context.message()); }
        });
        Network.registerPacket(SettingsRequest.TYPE, SettingsRequest.class, SettingsRequest.CODEC, context -> {
            if (context.side() != Side.SERVER || context.sender() == null) { return; }
            var player = context.sender();
            SettingsReply reply = edit(player, context.message());
            player.containerMenu.broadcastChanges(); player.inventoryMenu.broadcastChanges();
            Network.getNetworkHandler().sendToClient(reply, player);
        });
        Network.registerPacket(SettingsReply.TYPE, SettingsReply.class, SettingsReply.CODEC, context -> {
            if (context.side() == Side.CLIENT) { settingsReplies.accept(context.message()); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> states.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> states.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> states.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> invalidate(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((LivingEquipmentChangeEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getSlot() == EquipmentSlot.FEET) {
                State state = states.get(player.getUUID());
                if (state != null) {
                    if (state.permit != null && (player.getItemBySlot(EquipmentSlot.FEET) != state.permit.root
                            || !ItemStack.matches(state.permit.root, state.permit.snapshot))) { state.permit = null; }
                    // Equipment events also report ordinary component changes, not just slot replacements.
                    if (state.menu != null && (player.getInventory().getItem(state.menu.slot) != state.menu.root
                            || !ItemStack.matches(settingsSnapshot(state.menu.root), state.menu.snapshot))) { state.menu = null; }
                }
            }
        });
    }

    /** Client listeners are installed by a bounded client adapter, never linked from server handlers. */
    public static void clientReplies(Consumer<ActivationReply> activation, Consumer<SettingsReply> settings) {
        activationReplies = activation; settingsReplies = settings;
    }
    public static void clearClientReplies() { activationReplies = ignored -> {}; settingsReplies = ignored -> {}; }

    public static ActivationReply prepare(ServerPlayer player, Prepare request) {
        State state = state(player);
        if (request.sequence <= state.activationSequence || request.sequence <= 0) { return prepared(request.sequence, NONE); }
        state.activationSequence = request.sequence; state.permit = null;
        ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
        if (player.containerMenu != player.inventoryMenu || !JustDireGroundStompCompatibility.equipped(player, root)
                || !JustDireSettingsCompatibility.groundStompBinding(root, request.key, request.mouse) || JustDireGroundStompCompatibility.quote(root) == null) {
            return prepared(request.sequence, NONE);
        }
        UUID nonce = UUID.randomUUID();
        state.permit = new Permit(root, snapshot(root), request.key, request.mouse, nonce, player.level().getGameTime() + 40);
        return prepared(request.sequence, nonce);
    }

    public static boolean activate(ServerPlayer player, Activate request) {
        State state = states.get(player.getUUID());
        if (state == null || state.player != player || state.permit == null || request.nonce.equals(NONE)
                || state.activationSequence != request.sequence || !state.permit.nonce.equals(request.nonce)) { return false; }
        Permit permit = state.permit;
        state.permit = null; // Consume before validation/commit. No later expiry, failure or equipment can revive it.
        ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
        return request.mouse == permit.mouse && request.key == permit.key && player.level().getGameTime() <= permit.expires
                && player.containerMenu == player.inventoryMenu && root == permit.root && ItemStack.matches(root, permit.snapshot)
                && JustDireGroundStompCompatibility.activate(player, root, permit.snapshot, request.key, request.mouse);
    }

    public static SettingsReply edit(ServerPlayer player, SettingsRequest request) {
        State state = state(player);
        var menu = player.containerMenu;
        if (request.menu != menu.containerId || !JustDireSettingsCompatibility.nativeMenu(menu)
                || request.slot < 0 || request.slot >= player.getInventory().getContainerSize()
                || request.selection <= 0 || request.request <= 0) { return settingsReply(request, NONE, false); }
        if (request.operation == SELECT) {
            if (request.selection <= state.selection || !request.token.equals(NONE)) { return settingsReply(request, NONE, false); }
            state.selection = request.selection; state.menu = null; state.permit = null;
            var root = player.getInventory().getItem(request.slot);
            if (!JustDireSettingsCompatibility.mixed(root) || !hasSettings(root)) { return settingsReply(request, NONE, false); }
            state.menu = new MenuSession(menu, root, settingsSnapshot(root), request.slot, UUID.randomUUID(), request.request);
            return settingsReply(request, state.menu.token, true);
        }
        MenuSession session = state.menu;
        if (session == null || session.menu != menu || state.selection != request.selection || !session.token.equals(request.token)
                || session.slot != request.slot || player.getInventory().getItem(request.slot) != session.root
                || request.request <= session.lastRequest || !ItemStack.matches(session.snapshot, settingsSnapshot(session.root))) {
            return settingsReply(request, NONE, false);
        }
        session.lastRequest = request.request; // Rejected edits still cannot replay a toggle.
        if (request.operation == DESELECT) {
            state.menu = null; state.permit = null;
            return settingsReply(request, NONE, true);
        }
        boolean accepted = JustDireSettingsCompatibility.edit(session.root, request);
        if (accepted) { session.snapshot = settingsSnapshot(session.root); state.permit = null; }
        return settingsReply(request, accepted ? session.token : NONE, accepted);
    }

    private static boolean hasSettings(ItemStack root) {
        return java.util.List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp").stream()
                .anyMatch(name -> JustDireSettingsCompatibility.metadata(root, name) != null);
    }

    private static ItemStack settingsSnapshot(ItemStack root) {
        var snapshot = snapshot(root);
        // Menu identity/settings must stay current, but ordinary worn timers, charging and wear
        // are not edits. Including them would destroy a valid selection every server tick.
        snapshot.remove(DataComponents.DAMAGE); snapshot.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        remove(snapshot, "ability_cooldowns"); remove(snapshot, "forge_energy");
        return snapshot;
    }
    private static ItemStack snapshot(ItemStack root) {
        var snapshot = root.copy();
        // Native records are immutable, but their list components can be mutated in-place by
        // native helpers. Stack.copy alone would let a stale permission follow those edits.
        copyList(snapshot, "ability_bindings"); copyList(snapshot, "ability_cooldowns");
        return snapshot;
    }
    @SuppressWarnings("unchecked") private static void copyList(ItemStack stack, String path) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).ifPresent(holder -> {
            var type = (DataComponentType<java.util.List<Object>>) holder.value();
            if (stack.has(type)) { stack.set(type, java.util.List.copyOf(stack.get(type))); }
        });
    }
    private static void remove(ItemStack stack, String path) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path))
                .ifPresent(type -> stack.remove(type.value()));
    }

    private static State state(ServerPlayer player) {
        State state = states.get(player.getUUID());
        if (state == null || state.player != player) { states.put(player.getUUID(), state = new State(player)); }
        return state;
    }
    private static void invalidate(UUID player) {
        State state = states.get(player);
        if (state != null) { state.menu = null; state.permit = null; }
    }
    private static void tick(MinecraftServer server) {
        states.values().removeIf(state -> server.getPlayerList().getPlayer(state.player.getUUID()) != state.player);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = states.get(player.getUUID());
            if (state != null) {
                if (state.permit != null && (player.containerMenu != player.inventoryMenu
                        || player.getItemBySlot(EquipmentSlot.FEET) != state.permit.root
                        || !ItemStack.matches(state.permit.root, state.permit.snapshot)
                        || player.level().getGameTime() > state.permit.expires)) { state.permit = null; }
                if (state.menu != null && (player.containerMenu != state.menu.menu
                        || player.getInventory().getItem(state.menu.slot) != state.menu.root
                        || !ItemStack.matches(state.menu.snapshot, settingsSnapshot(state.menu.root)))) { state.menu = null; }
            }
            JustDireGroundStompCompatibility.tickEquipped(player);
        }
    }

    private static ActivationReply prepared(int sequence, UUID nonce) { return new ActivationReply(sequence, nonce, true, !nonce.equals(NONE)); }
    private static SettingsReply settingsReply(SettingsRequest request, UUID token, boolean accepted) {
        return new SettingsReply(request.menu, request.selection, request.request, token, accepted);
    }
    private static final class State {
        final ServerPlayer player;
        int activationSequence, selection;
        Permit permit;
        MenuSession menu;
        State(ServerPlayer player) { this.player = player; }
    }
    private record Permit(ItemStack root, ItemStack snapshot, int key, boolean mouse, UUID nonce, long expires) {}
    private static final class MenuSession {
        final AbstractContainerMenu menu;
        final ItemStack root;
        final int slot;
        final UUID token;
        ItemStack snapshot;
        int lastRequest;
        MenuSession(AbstractContainerMenu menu, ItemStack root, ItemStack snapshot, int slot, UUID token, int lastRequest) {
            this.menu = menu; this.root = root; this.snapshot = snapshot; this.slot = slot; this.token = token; this.lastRequest = lastRequest;
        }
    }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "jdt_" + path));
    }
    private static void exhausted(RegistryFriendlyByteBuf buffer) {
        if (buffer.isReadable()) { throw new DecoderException("Trailing mixed JDT payload bytes"); }
    }

    public record Prepare(int sequence, int key, boolean mouse) implements CustomPacketPayload {
        public static final Type<Prepare> TYPE = JustDireActiveNetwork.type("prepare");
        public static final StreamCodec<RegistryFriendlyByteBuf, Prepare> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
        }, buffer -> { var packet = new Prepare(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Activate(int sequence, UUID nonce, int key, boolean mouse) implements CustomPacketPayload {
        public static final Type<Activate> TYPE = JustDireActiveNetwork.type("activate");
        public static final StreamCodec<RegistryFriendlyByteBuf, Activate> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
        }, buffer -> { var packet = new Activate(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ActivationReply(int sequence, UUID nonce, boolean prepared, boolean accepted) implements CustomPacketPayload {
        public static final Type<ActivationReply> TYPE = JustDireActiveNetwork.type("activation_reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActivationReply> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeBoolean(packet.prepared); buffer.writeBoolean(packet.accepted);
        }, buffer -> { var packet = new ActivationReply(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SettingsRequest(int menu, int slot, int selection, int request, UUID token, int operation,
                                  String ability, int value, int key, boolean mouse, boolean equipped) implements CustomPacketPayload {
        public static final Type<SettingsRequest> TYPE = JustDireActiveNetwork.type("settings_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsRequest> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.menu); buffer.writeVarInt(packet.slot); buffer.writeVarInt(packet.selection); buffer.writeVarInt(packet.request);
            buffer.writeUUID(packet.token); buffer.writeVarInt(packet.operation); buffer.writeUtf(packet.ability, 32);
            buffer.writeVarInt(packet.value); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse); buffer.writeBoolean(packet.equipped);
        }, buffer -> {
            var packet = new SettingsRequest(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUUID(),
                    buffer.readVarInt(), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
            exhausted(buffer); return packet;
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SettingsReply(int menu, int selection, int request, UUID token, boolean accepted) implements CustomPacketPayload {
        public static final Type<SettingsReply> TYPE = JustDireActiveNetwork.type("settings_reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsReply> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.menu); buffer.writeVarInt(packet.selection); buffer.writeVarInt(packet.request);
            buffer.writeUUID(packet.token); buffer.writeBoolean(packet.accepted);
        }, buffer -> { var packet = new SettingsReply(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
