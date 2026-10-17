package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import commonnetwork.api.Network;
import commonnetwork.networking.data.Side;
import games.alejandrocoria.mismatchedboots.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Held toggles, separate menu edit sessions and consumed-once bounded batches; server thread only. */
public final class JustDireActiveNetwork {
    public static final int SELECT = 0, TOGGLE = 1, VALUE = 2, PARTICLES = 3, BINDING = 4, DESELECT = 6;
    public static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, State> states = new HashMap<>();
    private static final Set<ServerPlayer> executing = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Consumer<ActivationReply> activationReplies = ignored -> {};
    private static Consumer<SettingsReply> settingsReplies = ignored -> {};

    private JustDireActiveNetwork() {}

    public static void initialize() {
        // These mod-owned routes register quietly without JDT. Optional lookup never grants access.
        Network.registerPacket(HeldToggle.TYPE, HeldToggle.class, HeldToggle.CODEC, context -> {
            if (context.side() != Side.SERVER || context.sender() == null) { return; }
            var player = context.sender();
            if (JustDireHeldToggleCompatibility.toggle(player, context.message().hand())) {
                invalidate(player.getUUID());
                player.inventoryMenu.broadcastChanges();
            }
        });
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
            Network.getNetworkHandler().sendToClient(new ActivationReply(context.message().sequence(), context.message().nonce(), false, accepted, List.of()), player);
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
                    prune(state);
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
        if (executing.contains(player)) { return prepared(request.sequence, NONE); }
        State state = state(player);
        if (request.sequence <= state.activationSequence || request.sequence <= 0) { return prepared(request.sequence, NONE); }
        state.activationSequence = request.sequence; state.permit = null;
        var targets = new ArrayList<Admission>();
        for (int slot = 0; slot <= 40; slot++) {
            if (!JustDireAbilityTarget.supportedSlot(slot)) { continue; }
            var root = player.getInventory().getItem(slot);
            var target = new JustDireAbilityTarget(player, slot, root);
            if (!target.valid()) { continue; }
            int actions = 0;
            if (JustDireSettingsCompatibility.groundStompBinding(target, request.key, request.mouse)
                    && JustDireGroundStompCompatibility.quote(root) != null) { actions |= JustDireActiveState.GROUND_STOMP; }
            if (JustDireSettingsCompatibility.earthquakeBinding(target, request.key, request.mouse)
                    && JustDireEarthquakeCompatibility.quote(root) != null) { actions |= JustDireActiveState.EARTHQUAKE; }
            if (actions != 0 || !JustDireSettingsCompatibility.boundPassives(target, request.key, request.mouse).isEmpty()) {
                targets.add(new Admission(target, actions == 0 ? passiveInputSnapshot(root) : snapshot(root), actions));
            }
        }
        if (targets.isEmpty()) { return prepared(request.sequence, NONE); }
        UUID nonce = UUID.randomUUID();
        long now = player.level().getGameTime();
        state.permit = new Permit(List.copyOf(targets), new HashSet<>(), player.level(), request.key, request.mouse, nonce, now, now + 40);
        return new ActivationReply(request.sequence, nonce, true, true, targets.stream().map(a -> a.target.slot()).toList());
    }

    public static boolean activate(ServerPlayer player, Activate request) {
        State state = states.get(player.getUUID());
        if (state == null || state.player != player || state.permit == null || request.nonce.equals(NONE)
                || state.activationSequence != request.sequence || !state.permit.nonce.equals(request.nonce)) { return false; }
        Permit permit = state.permit;
        state.permit = null; // Consume before validation/commit. No later expiry, failure or equipment can revive it.
        if (!(request.mouse == permit.mouse && request.key == permit.key && player.level().getGameTime() <= permit.expires
                && player.level() == permit.level && player.containerMenu == player.inventoryMenu && validSlots(request.slots))) { return false; }
        // A subset can only attenuate this batch. Validate the whole request before any effects.
        if (request.slots.stream().anyMatch(slot -> permit.targets.stream().noneMatch(a -> a.target.slot() == slot))) { return false; }
        // Native useAbility runs active actions before every bound passive switch. A failed quote
        // (disabled tool, depletion or cooldown) must not prevent re-enabling a passive setting.
        if (!executing.add(player)) { return false; }
        try {
            boolean acted = false;
            for (Admission admission : permit.targets) {
                if (!request.slots.contains(admission.target.slot()) || permit.invalid.contains(admission.target.slot())
                        || !matches(admission, permit)) { continue; }
                acted |= activateTarget(admission, request, permit.level);
            }
            return acted;
        } finally { executing.remove(player); }
    }

    private static boolean activateTarget(Admission admission, Activate request, ServerLevel level) {
        var target = admission.target;
        var root = target.root();
        // Admission is fixed at prepare; only a proven unrelated worn clock can refresh expected.
        var expected = snapshot(root);
        boolean acted = false;
        int first = JustDireEarthquakeCompatibility.groundStompFirst() ? JustDireActiveState.GROUND_STOMP : JustDireActiveState.EARTHQUAKE;
        for (int action : new int[]{first, first == JustDireActiveState.GROUND_STOMP ? JustDireActiveState.EARTHQUAKE : JustDireActiveState.GROUND_STOMP}) {
            if ((admission.actions & action) == 0) { continue; }
            if (!target.valid() || target.player().level() != level || !ItemStack.matches(root, expected)) { return acted; }
            acted |= action == JustDireActiveState.GROUND_STOMP
                    ? JustDireGroundStompCompatibility.activate(target, expected, request.key, request.mouse)
                    : JustDireEarthquakeCompatibility.activate(target, expected, request.key, request.mouse);
        }
        if (!target.valid() || target.player().level() != level || !ItemStack.matches(root, expected)) { return acted; }
        boolean toggled = JustDireSettingsCompatibility.toggleBoundPassives(target, request.key, request.mouse);
        return acted || toggled;
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
        return JustDireEarthquakeCompatibility.hasSettings(root) || java.util.List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "lavarepair").stream()
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
    /** Free passive switches depend on identity/settings, not ordinary charging, wear or timer ticks. */
    public static ItemStack passiveInputSnapshot(ItemStack root) { return settingsSnapshot(root); }
    private static boolean matches(Admission admission, Permit permit) {
        var target = admission.target;
        if (!target.valid() || target.player().level() != permit.level) { return false; }
        return admission.actions == 0 ? ItemStack.matches(passiveInputSnapshot(target.root()), admission.snapshot)
                // Prepare may occur on either side of the same tick's Post listener; allow that one tick, not arbitrary timer removal.
                : target.equipped() ? JustDireActiveState.paidMatches(target.root(), admission.snapshot, admission.actions,
                        target.player().level().getGameTime() - permit.issued + 1)
                : ItemStack.matches(target.root(), admission.snapshot);
    }
    private static ItemStack snapshot(ItemStack root) {
        return JustDireActiveState.snapshot(root);
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
    private static void prune(State state) {
        if (state.permit == null) { return; }
        var permit = state.permit;
        if (state.player.level() != permit.level || state.player.level().getGameTime() > permit.expires
                || state.player.containerMenu != state.player.inventoryMenu) {
            state.permit = null; return;
        }
        // Monotonic per-slot invalidation: putting a removed reference back cannot revive intent.
        for (Admission admission : permit.targets) {
            if (!permit.invalid.contains(admission.target.slot()) && !matches(admission, permit)) { permit.invalid.add(admission.target.slot()); }
        }
        if (permit.invalid.size() == permit.targets.size()) { state.permit = null; }
    }
    private static void tick(MinecraftServer server) {
        states.values().removeIf(state -> server.getPlayerList().getPlayer(state.player.getUUID()) != state.player);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = states.get(player.getUUID());
            if (state != null) {
                prune(state);
                if (state.menu != null && (player.containerMenu != state.menu.menu
                        || player.getInventory().getItem(state.menu.slot) != state.menu.root
                        || !ItemStack.matches(state.menu.snapshot, settingsSnapshot(state.menu.root)))) { state.menu = null; }
            }
            JustDireGroundStompCompatibility.tickEquipped(player);
            JustDireEarthquakeCompatibility.tickEquipped(player);
        }
    }

    private static ActivationReply prepared(int sequence, UUID nonce) { return new ActivationReply(sequence, nonce, true, !nonce.equals(NONE), List.of()); }
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
    private record Admission(JustDireAbilityTarget target, ItemStack snapshot, int actions) {}
    private record Permit(List<Admission> targets, Set<Integer> invalid, ServerLevel level, int key, boolean mouse, UUID nonce, long issued, long expires) {}
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

    public record HeldToggle(InteractionHand hand) implements CustomPacketPayload {
        public static final Type<HeldToggle> TYPE = JustDireActiveNetwork.type("held_toggle");
        public static final StreamCodec<RegistryFriendlyByteBuf, HeldToggle> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeEnum(packet.hand);
        }, buffer -> {
            var packet = new HeldToggle(buffer.readEnum(InteractionHand.class));
            exhausted(buffer); return packet;
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Prepare(int sequence, int key, boolean mouse) implements CustomPacketPayload {
        public static final Type<Prepare> TYPE = JustDireActiveNetwork.type("prepare");
        public static final StreamCodec<RegistryFriendlyByteBuf, Prepare> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
        }, buffer -> { var packet = new Prepare(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static boolean validSlots(List<Integer> slots) {
        if (slots.isEmpty() || slots.size() > 41) { return false; }
        var seen = new HashSet<Integer>();
        return slots.stream().allMatch(slot -> slot != null && JustDireAbilityTarget.supportedSlot(slot) && seen.add(slot));
    }

    private static List<Integer> readSlots(RegistryFriendlyByteBuf buffer, boolean allowEmpty) {
        int size = buffer.readVarInt();
        if (size == 0 && allowEmpty) { return List.of(); }
        if (size <= 0 || size > 41) { throw new DecoderException("Invalid mixed JDT slot count"); }
        var slots = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) { slots.add(buffer.readVarInt()); }
        if (!validSlots(slots)) { throw new DecoderException("Invalid mixed JDT slot subset"); }
        return slots;
    }

    public record Activate(int sequence, UUID nonce, int key, boolean mouse, List<Integer> slots) implements CustomPacketPayload {
        public Activate { slots = List.copyOf(slots); }
        public static final Type<Activate> TYPE = JustDireActiveNetwork.type("activate");
        public static final StreamCodec<RegistryFriendlyByteBuf, Activate> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
            buffer.writeVarInt(packet.slots.size()); packet.slots.forEach(buffer::writeVarInt);
        }, buffer -> {
            int sequence = buffer.readVarInt(); UUID nonce = buffer.readUUID(); int key = buffer.readVarInt(); boolean mouse = buffer.readBoolean();
            var packet = new Activate(sequence, nonce, key, mouse, readSlots(buffer, false)); exhausted(buffer); return packet;
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ActivationReply(int sequence, UUID nonce, boolean prepared, boolean accepted, List<Integer> slots) implements CustomPacketPayload {
        public ActivationReply { slots = List.copyOf(slots); }
        public static final Type<ActivationReply> TYPE = JustDireActiveNetwork.type("activation_reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActivationReply> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeBoolean(packet.prepared); buffer.writeBoolean(packet.accepted);
            buffer.writeVarInt(packet.slots.size()); packet.slots.forEach(buffer::writeVarInt);
        }, buffer -> {
            var packet = new ActivationReply(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean(), readSlots(buffer, true));
            exhausted(buffer); return packet;
        });
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
