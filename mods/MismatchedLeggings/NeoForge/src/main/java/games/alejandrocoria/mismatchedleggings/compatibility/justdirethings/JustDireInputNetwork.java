package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** One gameplay press prepares identities; the reply may only narrow that server-owned batch. */
public final class JustDireInputNetwork {
    private static final Map<UUID, State> states = new HashMap<>();
    private static Consumer<InputReply> replies = ignored -> {};

    private JustDireInputNetwork() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> states.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> states.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> states.remove(event.getEntity().getUUID()));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(InputRequest.TYPE, InputRequest.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            var reply = handle(player, packet);
            player.inventoryMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, reply);
        });
        registrar.playToClient(InputReply.TYPE, InputReply.CODEC, (packet, context) -> replies.accept(packet));
        registrar.playToServer(HeldToggle.TYPE, HeldToggle.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            JustDireHeldToggleCompatibility.toggle(player, packet.hand);
            player.inventoryMenu.broadcastChanges();
        });
    }

    public static void clientReplies(Consumer<InputReply> receiver) { replies = receiver; }

    public static int legsSlot() { return EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE); }

    public static ItemStack snapshot(ItemStack root) { return JustDireSettingsNetwork.snapshot(root); }

    public static InputReply handle(ServerPlayer player, InputRequest packet) {
        var state = states.computeIfAbsent(player.getUUID(), ignored -> new State());
        var rejected = new InputReply(packet.sequence, JustDireSettingsNetwork.NONE, List.of());
        if (packet.sequence <= 0 || !JustDireSettings.validBindingInput(packet.key, packet.mouse)
                || player.containerMenu != player.inventoryMenu || player.isSpectator() || !player.isAlive()) {
            state.batch = null;
            return rejected;
        }
        if (packet.token.equals(JustDireSettingsNetwork.NONE)) {
            if (packet.sequence <= state.sequence || !packet.slots.isEmpty()) { return rejected; }
            state.sequence = packet.sequence;
            var targets = new ArrayList<Target>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (!JustDireSettingsNetwork.supportedSlot(slot)) { continue; }
                var root = player.getInventory().getItem(slot);
                if (!JustDireSettings.boundPassives(root, packet.key, packet.mouse, slot == legsSlot()).isEmpty()) {
                    targets.add(new Target(slot, root, snapshot(root)));
                }
            }
            state.batch = new Batch(UUID.randomUUID(), packet.key, packet.mouse, player.level().getGameTime(), targets);
            return new InputReply(packet.sequence, state.batch.token, targets.stream().map(Target::slot).toList());
        }
        var batch = state.batch;
        state.batch = null; // A nonce is consumed even if the activation is refused.
        if (batch == null || packet.sequence != state.sequence || !batch.token.equals(packet.token)
                || batch.key != packet.key || batch.mouse != packet.mouse || player.level().getGameTime() - batch.time > 100
                || packet.slots.size() != packet.slots.stream().distinct().count()
                || !batch.targets.stream().map(Target::slot).toList().containsAll(packet.slots)) {
            return rejected;
        }
        for (Target target : batch.targets) {
            if (!packet.slots.contains(target.slot)) { continue; }
            var root = player.getInventory().getItem(target.slot);
            if (root == target.root && ItemStack.matches(target.snapshot, snapshot(root))) {
                JustDireSettings.toggleBoundPassives(player, root, packet.key, packet.mouse, target.slot == legsSlot());
            }
        }
        return rejected;
    }

    private static final class State {
        int sequence;
        Batch batch;
    }

    private record Target(int slot, ItemStack root, ItemStack snapshot) {}
    private record Batch(UUID token, int key, boolean mouse, long time, List<Target> targets) {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "jdt_" + name));
    }

    private static List<Integer> readSlots(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 38) { throw new DecoderException("Invalid JDT passive batch length"); }
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < size; i++) { slots.add(buffer.readVarInt()); }
        if (buffer.isReadable()) { throw new DecoderException("Trailing JDT input bytes"); }
        return List.copyOf(slots);
    }

    private static void writeSlots(RegistryFriendlyByteBuf buffer, List<Integer> slots) {
        buffer.writeVarInt(slots.size());
        slots.forEach(buffer::writeVarInt);
    }

    public record InputRequest(int sequence, int key, boolean mouse, UUID token, List<Integer> slots) implements CustomPacketPayload {
        public InputRequest { slots = List.copyOf(slots); }
        public static final Type<InputRequest> TYPE = JustDireInputNetwork.type("input");
        public static final StreamCodec<RegistryFriendlyByteBuf, InputRequest> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence);
            buffer.writeVarInt(packet.key);
            buffer.writeBoolean(packet.mouse);
            buffer.writeUUID(packet.token);
            writeSlots(buffer, packet.slots);
        }, buffer -> new InputRequest(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUUID(), readSlots(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record InputReply(int sequence, UUID token, List<Integer> slots) implements CustomPacketPayload {
        public InputReply { slots = List.copyOf(slots); }
        public static final Type<InputReply> TYPE = JustDireInputNetwork.type("input_reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, InputReply> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.sequence);
            buffer.writeUUID(packet.token);
            writeSlots(buffer, packet.slots);
        }, buffer -> new InputReply(buffer.readVarInt(), buffer.readUUID(), readSlots(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HeldToggle(InteractionHand hand) implements CustomPacketPayload {
        public static final Type<HeldToggle> TYPE = JustDireInputNetwork.type("held_toggle");
        public static final StreamCodec<RegistryFriendlyByteBuf, HeldToggle> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeEnum(packet.hand), buffer -> {
                    var hand = buffer.readEnum(InteractionHand.class);
                    if (buffer.isReadable()) { throw new DecoderException("Trailing JDT held input bytes"); }
                    return new HeldToggle(hand);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
