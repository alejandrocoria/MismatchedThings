package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Temporary packet driver only: real production handlers/ticks and normal client stack synchronization. */
final class NeoForgeJustDireGroundStompTransportTest {
    private static final String PREFIX = "just-dire-ground-stomp-transport";
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static Future<?> job;
    private static boolean initialized, started, finished;
    private static int wait, paused, menuId;
    private static ItemStack serverRoot;
    private static JustDireActiveNetwork.ActivationReply activation;
    private static JustDireActiveNetwork.SettingsReply settings;
    private static JustDireActiveNetwork.Activate consumed;
    private static UUID token;

    private NeoForgeJustDireGroundStompTransportTest() {}
    static void tick(Minecraft client) {
        if (finished || client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
        try {
            if (!initialized) { initialize(client); initialized = true; }
            if (job != null) { if (!job.isDone()) { return; } job.get(); job = null; }
            Step step = steps.peek();
            if (step == null) {
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt", report + "temporary client packet driver, production handlers/action/tick; no UI/input or ordinary-client acceptance\n");
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n"); finished = true;
            } else if (!started) { step.start.run(); started = true; wait = 0; }
            else if (step.done.getAsBoolean()) { report.append(step.name).append('\n'); steps.remove(); started = false; }
            else if (++wait > 600) { throw new AssertionError("Timed out: " + step.name + "; reply=" + activation + "/" + settings); }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); finished = true; }
        if (finished) { JustDireActiveNetwork.clearClientReplies(); client.stop(); }
    }

    private static void initialize(Minecraft client) throws Exception {
        require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Client not hidden/muted");
        JustDireGroundStompTestSupport.initializeApi();
        JustDireActiveNetwork.clientReplies(reply -> activation = reply, reply -> settings = reply);
        add("real production prepare/action: one bill and positive timer reach ordinary equipment packets", () -> server(client, player -> {
            player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
            var boot = JustDireGroundStompTestSupport.boots("eclipsealloy", 12_000);
            serverRoot = MixedBootsFactory.createPair(boot, new ItemStack(Items.DIAMOND_BOOTS)).primary();
            configure(serverRoot); player.setItemSlot(EquipmentSlot.FEET, serverRoot); player.inventoryMenu.broadcastChanges();
        }), () -> client.screen == null && charge(feet(client)) == 6_000);
        add("server-issued nonce travels through bundled CommonNetworking", () -> prepare(1), () -> activation != null && activation.sequence() == 1 && activation.prepared() && activation.accepted());
        add("prepared permit activates exactly one live root", () -> {
            consumed = new JustDireActiveNetwork.Activate(1, activation.nonce(), 71, false); activation = null; send(consumed);
        }, () -> activation != null && !activation.prepared() && activation.accepted() && charge(feet(client)) == 1_000 && timer(feet(client)) > 0);
        add("consumed activation replay rejected through real transport while normal timer progresses", () -> { activation = null; send(consumed); },
                () -> activation != null && !activation.accepted() && charge(feet(client)) == 1_000);
        add("unequipped root pauses through natural server ticks", () -> server(client, player -> {
            paused = timer(serverRoot); require(paused > 0, "No pending timer to pause"); player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.getInventory().setItem(2, serverRoot); player.inventoryMenu.broadcastChanges();
        }), () -> feet(client).isEmpty() && timer(client.player.getInventory().getItem(2)) == paused && wait > 30);
        add("paused root was not advanced; disabled/depleted re-equipped root resumes", () -> server(client, player -> {
            require(timer(serverRoot) == paused, "Unequipped timer advanced");
            set(serverRoot, "tool_enabled", false); set(serverRoot, "groundstomp_upgrade_installed", false); set(serverRoot, "forge_energy", 0);
            player.getInventory().setItem(2, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, serverRoot); player.inventoryMenu.broadcastChanges();
        }), () -> charge(feet(client)) == 0 && timer(feet(client)) > 0 && timer(feet(client)) < paused);
        add("production worn timer expires naturally even while tool/installation disabled and FE exhausted", () -> {}, () -> !feet(client).isEmpty() && timer(feet(client)) == -1);
        add("consumed replay still rejects after actual natural cooldown expiry", () -> { activation = null; send(consumed); },
                () -> activation != null && !activation.accepted() && charge(feet(client)) == 0 && timer(feet(client)) == -1);
        add("native settings menu opens normally, with no mixed UI hook", () -> {
            server(client, player -> { set(serverRoot, "groundstomp_upgrade_installed", true); player.inventoryMenu.broadcastChanges(); });
            nativeMenu();
        }, () -> client.screen instanceof AbstractContainerScreen<?> screen && screen.getClass().getName().equals("com.direwolf20.justdirethings.client.screens.ToolSettingScreen"));
        add("production selected-root settings token through bundled transport", () -> {
            menuId = ((AbstractContainerScreen<?>) client.screen).getMenu().containerId;
            settings = null; send(request(1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        }, () -> settings != null && settings.accepted() && settings.request() == 1);
        add("zero FE retains full tier settings range; normal inventory packets carry edit", () -> {
            token = settings.token(); settings = null; send(request(2, token, 2, "groundstomp", 5, 71, false, true));
        }, () -> settings != null && settings.request() == 2 && settings.accepted() && value(feet(client)) == 5);
        add("forged mouse setting rejected without resetting accepted root value/resources", () -> {
            settings = null; send(request(3, token, 4, "groundstomp", 2, 71, true, true));
        }, () -> settings != null && settings.request() == 3 && !settings.accepted() && value(feet(client)) == 5 && charge(feet(client)) == 0);
        add("selection token cannot authorize action", () -> { activation = null; send(new JustDireActiveNetwork.Activate(1, token, 71, false)); },
                () -> activation != null && !activation.accepted());
        add("ordinary close packet invalidates edit session", () -> client.screen.onClose(), () -> client.screen == null);
        add("stale edit after actual menu close rejects through transport", () -> { settings = null; send(request(4, token, 1, "groundstomp", 0, 71, false, true)); },
                () -> settings != null && settings.request() == 4 && !settings.accepted() && value(feet(client)) == 5);
        add("server/client provenance and live resource state agree after action/edits/ticking", () -> server(client, player -> {
            require(player.getItemBySlot(EquipmentSlot.FEET) == serverRoot && charge(serverRoot) == 0 && timer(serverRoot) == -1
                    && value(serverRoot) == 5 && ModContent.getMixedBootsData(serverRoot).equals(ModContent.getMixedBootsData(feet(client))), "Live root/state/provenance diverged");
        }), () -> true);
    }
    private static void configure(ItemStack root) {
        try {
            set(root, "groundstomp_upgrade_installed", true); set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                    .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true)));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void nativeMenu() {
        try { ClientPacketDistributor.sendToServer((CustomPacketPayload) Class.forName("com.direwolf20.justdirethings.common.network.data.ToolSettingsGUIPayload").getConstructor().newInstance()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static JustDireActiveNetwork.SettingsRequest request(int number, UUID token, int operation, String ability, int value, int key, boolean mouse, boolean equipped) {
        return new JustDireActiveNetwork.SettingsRequest(menuId, 36, 1, number, token, operation, ability, value, key, mouse, equipped);
    }
    private static void prepare(int sequence) { activation = null; send(new JustDireActiveNetwork.Prepare(sequence, 71, false)); }
    private static void send(Object packet) { Network.getNetworkHandler().sendToServer(packet); }
    private static void server(Minecraft client, Consumer<ServerPlayer> task) {
        var id = client.player.getUUID(); var server = client.getSingleplayerServer(); require(server != null, "No integrated server");
        job = server.submit(() -> { var player = server.getPlayerList().getPlayer(id); require(player != null, "No actual server player"); task.accept(player); });
    }
    private static ItemStack feet(Minecraft client) { return client.player.getItemBySlot(EquipmentSlot.FEET); }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static int value(ItemStack root) { return root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_value"), -1); }
    private static int timer(ItemStack root) {
        if (root.isEmpty()) { return -1; }
        try { return JustDireGroundStompTestSupport.timer(root); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static <T> void set(ItemStack root, String path, T value) { JustDireAttributeTestSupport.set(root, path, value); }
    private static void add(String name, Runnable start, BooleanSupplier done) { steps.add(new Step(name, start, done)); }
    private static void require(boolean condition, String text) { if (!condition) { throw new AssertionError(text); } }
    private record Step(String name, Runnable start, BooleanSupplier done) {}
}
