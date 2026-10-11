package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in one-use activation permits, independent of native menu/edit tokens. */
final class JustDireGroundStompSpikeNetwork {
    static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static boolean initialized;
    static boolean available = true; // Explicit fixture-only unavailable-route seam.
    static Mob observedTarget;
    static volatile Observation observation;

    private JustDireGroundStompSpikeNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("ground-stomp-input-spike-1");
        registrar.playToServer(Prepare.TYPE, Prepare.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            try {
                var nonce = prepare(player, packet);
                player.inventoryMenu.broadcastChanges();
                context.reply(new Prepared(packet.sequence, nonce));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned action API changed", failure); }
        });
        registrar.playToServer(Activate.TYPE, Activate.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            var before = player.getItemBySlot(EquipmentSlot.FEET).copy();
            double motion = motion();
            try {
                boolean accepted = activate(player, packet);
                var after = player.getItemBySlot(EquipmentSlot.FEET).copy();
                observation = new Observation(packet, accepted, before, after, motion, motion(),
                        initialized && !after.isEmpty() ? NeoForgeJustDireGroundStompSpike.timer(after) : -1);
                player.inventoryMenu.broadcastChanges();
                context.reply(new Result(packet.sequence, packet.nonce, accepted));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned action API changed", failure); }
        });
        registrar.playToClient(Prepared.TYPE, Prepared.CODEC, (packet, context) -> JustDireGroundStompSpikeInput.prepared(packet));
        registrar.playToClient(Result.TYPE, Result.CODEC, (packet, context) -> JustDireGroundStompSpikeInput.result = packet);
    }

    private static UUID prepare(ServerPlayer player, Prepare packet) throws ReflectiveOperationException {
        var session = sessions.computeIfAbsent(player.getUUID(), ignored -> new Session(player));
        if (session.player != player) { sessions.put(player.getUUID(), session = new Session(player)); }
        if (packet.sequence <= session.sequence) { return NONE; }
        session.sequence = packet.sequence;
        session.invalidate();
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        if (!available || player.containerMenu != player.inventoryMenu || !binding(root, packet.key, packet.mouse)
                || JustDireSettingsSpikeSupport.view(root) == null) { return NONE; }
        if (!initialized) { NeoForgeJustDireGroundStompSpike.initializeApi(); initialized = true; }
        if (NeoForgeJustDireGroundStompSpike.timer(root) != -1 || NeoForgeJustDireGroundStompSpike.quote(root, false) == null) { return NONE; }
        session.root = root; session.snapshot = root.copy(); session.key = packet.key;
        return session.nonce = UUID.randomUUID();
    }

    private static boolean activate(ServerPlayer player, Activate packet) throws ReflectiveOperationException {
        var session = sessions.get(player.getUUID());
        if (session == null || session.player != player || packet.nonce.equals(NONE)
                || session.sequence != packet.sequence || !session.nonce.equals(packet.nonce)) { return false; }
        // Consume before preflight/payment. Cooldown expiry must never revive a replayed permit.
        session.nonce = NONE;
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        return available && player.containerMenu == player.inventoryMenu && root == session.root
                && ItemStack.matches(root, session.snapshot) && packet.key == session.key
                && binding(root, packet.key, packet.mouse)
                && NeoForgeJustDireGroundStompActionSpike.activate(player, root, session.snapshot, packet.key);
    }

    static boolean binding(ItemStack root, int key, boolean mouse) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("justdirethings") || !JustDireSettingsSpikeSupport.mixed(root)
                || mouse || key < 32 || key > 348
                || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_upgrade_installed"), false)
                || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("tool_enabled"), true)
                || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_toggle"), true)
                || root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_bindingmode"), 0) != 2) { return false; }
        for (var binding : root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of())) {
            var type = binding.getClass();
            if (type.getMethod("abilityName").invoke(binding).equals("groundstomp")
                    && (int) type.getMethod("key").invoke(binding) == key
                    && !(boolean) type.getMethod("isMouse").invoke(binding)
                    && (boolean) type.getMethod("requireEquipped").invoke(binding)) { return true; }
        }
        return false;
    }

    static void tick(MinecraftServer server) {
        sessions.values().removeIf(session -> server.getPlayerList().getPlayer(session.player.getUUID()) != session.player);
        if (!ModList.get().isLoaded("justdirethings")) { return; }
        try {
            for (var player : server.getPlayerList().getPlayers()) {
                var root = player.getItemBySlot(EquipmentSlot.FEET);
                var session = sessions.get(player.getUUID());
                if (session != null && !session.nonce.equals(NONE)
                        && (root != session.root || !ItemStack.matches(root, session.snapshot))) { session.invalidate(); }
                if (JustDireSettingsSpikeSupport.mixed(root)) { NeoForgeJustDireGroundStompActionSpike.tickRoot(player, root); }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned timer API changed", failure); }
    }

    static void clear() { sessions.clear(); observedTarget = null; observation = null; initialized = false; }
    private static double motion() { return observedTarget == null ? 0 : observedTarget.getDeltaMovement().horizontalDistance(); }
    private static void exhausted(RegistryFriendlyByteBuf buffer) {
        if (buffer.isReadable()) { throw new DecoderException("Trailing Ground Stomp spike bytes"); }
    }
    private static final class Session {
        final ServerPlayer player;
        int sequence;
        int key;
        UUID nonce = NONE;
        ItemStack root;
        ItemStack snapshot;
        Session(ServerPlayer player) { this.player = player; }
        void invalidate() { nonce = NONE; root = null; snapshot = null; }
    }
    record Observation(Activate packet, boolean accepted, ItemStack before, ItemStack after,
                       double motionBefore, double motionAfter, int cooldown) {}

    record Prepare(int sequence, int key, boolean mouse) implements CustomPacketPayload {
        static final Type<Prepare> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "stomp_prepare"));
        static final StreamCodec<RegistryFriendlyByteBuf, Prepare> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
        }, buffer -> { var packet = new Prepare(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    record Prepared(int sequence, UUID nonce) implements CustomPacketPayload {
        static final Type<Prepared> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "stomp_prepared"));
        static final StreamCodec<RegistryFriendlyByteBuf, Prepared> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce);
        }, buffer -> { var packet = new Prepared(buffer.readVarInt(), buffer.readUUID()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    record Activate(int sequence, UUID nonce, int key, boolean mouse) implements CustomPacketPayload {
        static final Type<Activate> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "stomp_activate"));
        static final StreamCodec<RegistryFriendlyByteBuf, Activate> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeVarInt(packet.key); buffer.writeBoolean(packet.mouse);
        }, buffer -> { var packet = new Activate(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    record Result(int sequence, UUID nonce, boolean accepted) implements CustomPacketPayload {
        static final Type<Result> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", "stomp_result"));
        static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence); buffer.writeUUID(packet.nonce); buffer.writeBoolean(packet.accepted);
        }, buffer -> { var packet = new Result(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()); exhausted(buffer); return packet; });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
