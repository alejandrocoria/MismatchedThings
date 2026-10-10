package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Real native screen/widgets and mixed wire edits; no active-ability implementation or manual UX claim. */
final class NeoForgeJustDireSettingsScreenSpike {
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static boolean initialized;
    private static boolean started;
    private static boolean finished;
    private static int wait;
    private static Future<?> job;
    private static ItemStack expected;
    private static ItemStack before;
    private static MixedBootsData sources;
    private static int pending;
    private static JustDireSettingsSpikeNetwork.Request delayed;

    private NeoForgeJustDireSettingsScreenSpike() {}

    static void tick(Minecraft client) {
        if (finished || client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
        try {
            if (!initialized) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Spike client must be hidden/muted");
                initialize(client); initialized = true;
            }
            if (job != null) {
                if (!job.isDone()) { return; }
                job.get(); job = null;
            }
            var step = steps.peek();
            if (step == null) {
                NetworkAcceptanceFiles.write("just-dire-settings-spike-report.txt", report + "test-only; native widgets/callbacks, synthetic mouse movement/key events and real packets; no physical/manual acceptance or mixed activation\n");
                NetworkAcceptanceFiles.write("just-dire-settings-spike-success.txt", "neoforge\n");
                finished = true;
            } else if (!started) {
                step.start.run(); started = true; wait = 0;
            } else if (step.done.get()) {
                report.append(step.name).append('\n'); steps.remove(); started = false;
            } else if (++wait > 600) { throw new AssertionError("Timed out: " + step.name + "; reply=" + JustDireSettingsSpikeClient.lastReply); }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-settings-spike", new AssertionError("Step: " + (steps.peek() == null ? "finish" : steps.peek().name), failure));
            finished = true;
        }
        if (finished) { client.stop(); }
    }

    private static void initialize(Minecraft client) {
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireSettingsVariant", "live");
        if (variant.equals("absent")) {
            add("optional absence: no native API/interface/mixin target required", () -> {
                require(!ModList.get().isLoaded("justdirethings"), "Absent variant has JDT");
                var pair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                require(JustDireSettingsSpikeSupport.view(pair.primary()) == null, "Absent API accepted root");
            });
            return;
        }
        add("assign temporary native settings key", () -> {
            var key = settingsKey();
            key.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_O));
            KeyMapping.resetMapping();
        });
        add("prepare empty hands and inventory without any native or mixed tool", () -> server(client, () -> {
            var player = owner(client); player.getInventory().clearContent(); player.inventoryMenu.broadcastChanges();
        }), () -> client.player.getInventory().isEmpty());
        open(client);
        add("empty-hand opening and native-to-empty internal inventory refresh on both sides", () -> {
            require(client.player.getMainHandItem().isEmpty()
                    && ((ItemStack) JustDireSettingsSpikeClient.field(client.screen, "tool")).isEmpty()
                    && map(client, "sliders").isEmpty(), "Empty-hand opening selected a tool");
            assertNoInternalSlots(((AbstractContainerScreen<?>) client.screen).getMenu());
            server(client, () -> assertNoInternalSlots(owner(client).containerMenu));
        });
        close(client);
        setup(client, false, false);
        open(client);
        add("native original retains its screen and tier widgets", () -> require(!map(client, "sliders").isEmpty(), "No native slider"));
        add("native enablement uses original callback and native wire handler", () -> click(icon(client), 0), () ->
                !client.player.getMainHandItem().getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_toggle"), true));
        close(client);
        if (!variant.equals("live")) {
            setup(client, true, false); open(client);
            add("unavailable UI/API keeps mixed-only initial screen unselected without changing root", () -> {
                require(((ItemStack) JustDireSettingsSpikeClient.field(client.screen, "tool")).isEmpty(), "Unavailable UI selected mixed root");
                require(map(client, "sliders").isEmpty() && ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET)), "Unavailable UI changed mixed root");
            });
            reject(client, "unavailable UI/API rejects forged mixed selection on wire", () -> new JustDireSettingsSpikeNetwork.Request(
                    ((AbstractContainerScreen<?>) client.screen).getMenu().containerId, 36,
                    ++JustDireSettingsSpikeClient.selection, ++JustDireSettingsSpikeClient.request, 0, 0, "", 0, -1, false, true));
            close(client); return;
        }
        for (boolean inverse : new boolean[]{false, true}) {
            setup(client, true, inverse); open(client);
            reject(client, "edit without acknowledged selection rejects", () -> new JustDireSettingsSpikeNetwork.Request(
                    ((AbstractContainerScreen<?>) client.screen).getMenu().containerId, 36,
                    JustDireSettingsSpikeClient.selection, ++JustDireSettingsSpikeClient.request, 0, 2, "groundstomp", 1, -1, false, true));
            select(client, 36);
            add("mixed " + inverse + ": real armor-slot selection keeps mixed identity and native Ground Stomp widgets at zero FE", () -> {
                require(JustDireSettingsSpikeClient.token != 0, "Selection not acknowledged");
                require(JustDireSettingsSpikeSupport.mixed((ItemStack) JustDireSettingsSpikeClient.field(client.screen, "tool"))
                        && map(client, "sliders").size() == 1 && map(client, "buttonToAbilityMap").size() == 1, "Unexpected mixed tool/widget scope");
                require(client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 0, "Fixture has charge");
            }, () -> JustDireSettingsSpikeClient.token != 0);
            edit(client, "native toggle callback edits root only", () -> click(icon(client), 0), () -> !rootToggle(client));
            edit(client, "native toggle re-enables without reinstalling", () -> click(icon(client), 0), () -> rootToggle(client));
            edit(client, "native slider lower endpoint", () -> slider(client).setValue(1), () -> rootValue(client, "groundstomp_value") == 1);
            edit(client, "native slider upper endpoint", () -> slider(client).setValue(5), () -> rootValue(client, "groundstomp_value") == 5);
            reveal(client);
            edit(client, "native particle control", () -> click((Button) map(client, "customSettingsButtons").get(icon(client)), 0),
                    () -> rootValue(client, "groundstomp_custom_setting") == 1);
            edit(client, "native binding capture assigns keyboard G", () -> {
                click((Button) map(client, "bindingButtons").get(icon(client)), 0);
                require(client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0)), "Native capture did not consume key");
            }, () -> boundKey(client) == GLFW.GLFW_KEY_G && rootValue(client, "groundstomp_bindingmode") == 2);
            edit(client, "native Escape capture removes binding", () -> {
                click((Button) map(client, "bindingButtons").get(icon(client)), 0);
                require(client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)), "Native removal did not consume Escape");
            }, () -> boundKey(client) == -1);
            reject(client, "wire bounds: strength 99", () -> packet(client, 2, "groundstomp", 99, -1, false, true));
            reject(client, "wire allowlist: unsupported Earthquake", () -> packet(client, 1, "earthquake", 0, -1, false, true));
            reject(client, "wire allowlist: unknown operation", () -> packet(client, 9, "groundstomp", 0, -1, false, true));
            reject(client, "wire bounds: malformed particles", () -> packet(client, 3, "groundstomp", -1, -1, false, true));
            reject(client, "wire bounds: malformed binding mode", () -> packet(client, 4, "groundstomp", 17, 71, false, true));
            reject(client, "wire bounds: malformed key", () -> packet(client, 4, "groundstomp", 2, -2, false, true));
            reject(client, "wire identity: foreign token", () -> {
                var request = packet(client, 2, "groundstomp", 1, -1, false, true);
                return new JustDireSettingsSpikeNetwork.Request(request.menu(), request.slot(), request.selection(), request.request(),
                        request.token() + 999, request.operation(), request.ability(), request.value(), request.key(), request.mouse(), request.equipped());
            });
            reject(client, "wire scope: mouse activation not selected", () -> packet(client, 4, "groundstomp", 2, 3, true, true));
            reject(client, "wire scope: inventory activation not selected", () -> packet(client, 4, "groundstomp", 2, 71, false, false));
            add("one valid toggle request", () -> {
                before = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
                delayed = packet(client, 1, "groundstomp", 0, -1, false, true);
                pending = delayed.request(); ClientPacketDistributor.sendToServer(delayed);
            }, () -> reply(client, true));
            reject(client, "duplicate toggle request must not toggle twice", () -> delayed);
            add("capture obsolete selection request", () -> delayed = packet(client, 2, "groundstomp", 2, -1, false, true));
            select(client, 36);
            reject(client, "obsolete selection token rejects after reselection", () -> delayed);
            add("capture mixed request before switching to an original tool", () -> {
                delayed = packet(client, 2, "groundstomp", 2, -1, false, true);
                server(client, () -> {
                    owner(client).setItemSlot(EquipmentSlot.MAINHAND, NeoForgeJustDireGroundStompSpike.boots("blazegold", 0));
                    owner(client).containerMenu.broadcastChanges();
                });
            }, () -> !client.player.getMainHandItem().isEmpty());
            select(client, 0);
            reject(client, "switching to original tool invalidates mixed edit session", () -> delayed);
            select(client, 36);
            close(client); open(client); select(client, 36);
            add("reopening preserves root controls and native slider values", () -> require(slider(client).getValueInt() == 5
                    && client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_custom_setting"), 0) == 1,
                    "Reopening lost controls"));
            add("capture old menu request", () -> delayed = packet(client, 2, "groundstomp", 2, -1, false, true));
            close(client); open(client); select(client, 36);
            reject(client, "closed/reopened menu rejects delayed edit", () -> delayed);
            add("replace selected server root with an equal copy", () -> server(client, () -> {
                var player = owner(client); player.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
                player.containerMenu.broadcastChanges();
            }));
            reject(client, "equal replacement rejects old target identity", () -> packet(client, 2, "groundstomp", 2, -1, false, true));
            select(client, 36);
            add("disable root installation externally", () -> server(client, () -> {
                JustDireAttributeTestSupport.set(owner(client).getItemBySlot(EquipmentSlot.FEET), "groundstomp_upgrade_installed", false);
                owner(client).containerMenu.broadcastChanges();
            }), () -> !client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_upgrade_installed"), true));
            reject(client, "disabled installation rejects editing despite historical source upgrade", () -> packet(client, 1, "groundstomp", 0, -1, false, true));
            close(client); open(client); select(client, 36);
            add("disabled root installation builds no Ground Stomp control", () -> require(map(client, "sliders").isEmpty(), "Disabled root inherited source widget"));
            close(client);
            add("remove installed flag explicitly", () -> server(client, () -> {
                owner(client).getItemBySlot(EquipmentSlot.FEET).remove(JustDireAttributeTestSupport.component("groundstomp_upgrade_installed"));
                owner(client).inventoryMenu.broadcastChanges();
            }), () -> !client.player.getItemBySlot(EquipmentSlot.FEET).has(JustDireAttributeTestSupport.component("groundstomp_upgrade_installed")));
            open(client); select(client, 36);
            add("absent installed flag also builds no control", () -> require(map(client, "sliders").isEmpty(), "Absent root flag inherited source widget"));
            close(client);
        }
        setup(client, true, false, true); open(client); select(client, 36);
        add("mixed-tier UI range stays at eligible maximum 5 with zero FE, independent of action-provider policy", () -> {
            require(JustDireSettingsSpikeSupport.parameter(JustDireSettingsSpikeSupport.view(client.player.getItemBySlot(EquipmentSlot.FEET)), "maxSlider") == 5,
                    "UI tier bound used depleted payment eligibility");
        });
        edit(client, "mixed-tier native slider writes upper endpoint on root", () -> slider(client).setValue(5), () -> rootValue(client, "groundstomp_value") == 5);
        close(client);
    }

    private static void setup(Minecraft client, boolean mixed, boolean inverse) {
        setup(client, mixed, inverse, false);
    }
    private static void setup(Minecraft client, boolean mixed, boolean inverse, boolean pairedTier) {
        add("prepare " + (mixed ? "mixed " + inverse : "native original"), () -> {
            var boot = NeoForgeJustDireGroundStompSpike.boots("eclipsealloy", 0);
            JustDireAttributeTestSupport.set(boot, "groundstomp_value", 3);
            var other = pairedTier ? NeoForgeJustDireGroundStompSpike.boots("blazegold", 0) : new ItemStack(Items.DIAMOND_BOOTS);
            var pair = mixed ? MixedBootsFactory.createPair(boot, other) : null;
            expected = mixed ? (inverse ? pair.inverse() : pair.primary()) : boot;
            sources = mixed ? ModContent.getMixedBootsData(expected).orElseThrow() : null;
            server(client, () -> {
                var player = owner(client); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
                player.setItemSlot(mixed ? EquipmentSlot.FEET : EquipmentSlot.MAINHAND, expected.copy());
                player.inventoryMenu.broadcastChanges();
            });
        }, () -> ItemStack.matches(expected, mixed ? client.player.getItemBySlot(EquipmentSlot.FEET) : client.player.getMainHandItem())
                && (!mixed || client.player.getMainHandItem().isEmpty()));
    }
    private static void open(Minecraft client) {
        add("wait for native settings-key context", () -> {},
                () -> client.screen == null && settingsKey().getKeyConflictContext().isActive());
        add("open real native menu through native settings-key consumption", () -> {
            require(client.screen == null && settingsKey().getKeyConflictContext().isActive(), "Native settings key is inactive");
            KeyMapping.click(settingsKey().getKey());
        },
                () -> client.screen != null && client.screen.getClass().getName().equals("com.direwolf20.justdirethings.client.screens.ToolSettingScreen"));
    }
    private static KeyMapping settingsKey() throws ReflectiveOperationException {
        return (KeyMapping) Class.forName("com.direwolf20.justdirethings.client.KeyBindings").getField("toolUI").get(null);
    }
    private static void assertNoInternalSlots(Object menu) {
        try {
            require(JustDireSettingsSpikeClient.field(menu, "componentItemHandler") == null
                    && ((java.util.List<?>) JustDireSettingsSpikeClient.field(menu, "dynamicSlots")).isEmpty(),
                    "Empty stack exposed internal inventory");
            require(menu.getClass().getMethod("getItemSlots", ItemStack.class).invoke(menu, ItemStack.EMPTY) == null,
                    "Empty stack query did not return no slots");
            var generator = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("justdirethings", "pocket_generator")));
            var handler = (ResourceHandler<?>) menu.getClass().getMethod("getItemSlots", ItemStack.class).invoke(menu, generator);
            require(handler != null && handler.size() > 0, "Guard disabled native non-empty internal inventory");
            var refresh = menu.getClass().getMethod("refreshSlots", ItemStack.class);
            refresh.invoke(menu, generator);
            require(((java.util.List<?>) JustDireSettingsSpikeClient.field(menu, "dynamicSlots")).size() == handler.size(),
                    "Native non-empty refresh lost internal slots");
            refresh.invoke(menu, ItemStack.EMPTY);
            require(JustDireSettingsSpikeClient.field(menu, "componentItemHandler") == null
                    && ((java.util.List<?>) JustDireSettingsSpikeClient.field(menu, "dynamicSlots")).isEmpty(),
                    "Refresh to EMPTY retained stale internal slots");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void close(Minecraft client) { add("close native menu", () -> client.screen.onClose(), () -> client.screen == null); }
    private static void select(Minecraft client, int index) {
        add("hover native inventory slot " + index, () -> {
            var screen = (AbstractContainerScreen<?>) client.screen;
            var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getSlotIndex() == index).findFirst().orElseThrow();
            move(client, (int) JustDireSettingsSpikeClient.field(screen, "leftPos") + slot.x + 8,
                    (int) JustDireSettingsSpikeClient.field(screen, "topPos") + slot.y + 8);
        }, () -> JustDireSettingsSpikeClient.field(client.screen, "hoveredSlot") instanceof Slot slot && slot.getSlotIndex() == index);
        add("native slot mouse selection " + index, () -> {
            var screen = client.screen;
            var slot = (Slot) JustDireSettingsSpikeClient.field(screen, "hoveredSlot");
            JustDireSettingsSpikeClient.token = 0;
            screen.mouseClicked(event((int) JustDireSettingsSpikeClient.field(screen, "leftPos") + slot.x + 8,
                    (int) JustDireSettingsSpikeClient.field(screen, "topPos") + slot.y + 8, 0), false);
        }, () -> !System.getProperty("mismatchedboots.runtimetest.justDireSettingsVariant", "live").equals("live")
                || !JustDireSettingsSpikeSupport.mixed((ItemStack) JustDireSettingsSpikeClient.field(client.screen, "tool"))
                || JustDireSettingsSpikeClient.token != 0);
    }
    private static void reveal(Minecraft client) {
        add("hover native Ground Stomp icon", () -> move(client, icon(client).getX() + 8, icon(client).getY() + 8),
                () -> JustDireSettingsSpikeClient.field(client.screen, "hoveredSlot") == null);
        add("native right-click reveals its binding/particle controls", () -> {
            var icon = icon(client); require(client.screen.mouseClicked(event(icon.getX() + 8, icon.getY() + 8, 1), false), "Right-click ignored");
        });
    }
    private static void edit(Minecraft client, String name, Action action, Check value) {
        add(name, () -> {
            before = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
            int oldRequest = JustDireSettingsSpikeClient.request;
            action.run(); pending = JustDireSettingsSpikeClient.request;
            require(pending > oldRequest, "Native callback emitted no mixed packet");
        }, () -> {
            if (!reply(client, true)) { return false; }
            require(value.get(), "Widget wrote incorrect root setting: " + name);
            return true;
        });
    }
    private static void reject(Minecraft client, String name, Supplier<JustDireSettingsSpikeNetwork.Request> packet) {
        add(name, () -> {
            before = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
            var request = packet.get(); pending = request.request(); JustDireSettingsSpikeClient.lastReply = null;
            ClientPacketDistributor.sendToServer(request);
        }, () -> reply(client, false));
    }
    private static boolean reply(Minecraft client, boolean accepted) {
        var reply = JustDireSettingsSpikeClient.lastReply;
        if (reply == null || reply.request() != pending) { return false; }
        require(reply.accepted() == accepted, "Unexpected edit reply " + reply);
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        if (accepted) { require(!ItemStack.matches(before, root), "Accepted widget did not change root"); }
        else { require(ItemStack.matches(before, root), "Rejected packet changed client root"); }
        var snapshot = root.copy();
        server(client, () -> {
            var current = owner(client).getItemBySlot(EquipmentSlot.FEET);
            require(ItemStack.matches(snapshot, current), "Server/client root differ after reply");
            require(sources.equals(ModContent.getMixedBootsData(current).orElseThrow()), "UI edit changed source provenance");
        });
        return true;
    }
    private static JustDireSettingsSpikeNetwork.Request packet(Minecraft client, int operation, String ability, int value, int key, boolean mouse, boolean equipped) {
        return JustDireSettingsSpikeClient.packet(client.screen, operation, ability, value, key, mouse, equipped);
    }
    private static Button icon(Minecraft client) {
        return (Button) map(client, "buttonToAbilityMap").entrySet().stream()
                .filter(entry -> entry.getValue().toString().equals("GROUNDSTOMP")).findFirst().orElseThrow().getKey();
    }
    private static ExtendedSlider slider(Minecraft client) { return (ExtendedSlider) map(client, "sliders").get(icon(client)); }
    private static int rootValue(Minecraft client, String name) {
        return client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireAttributeTestSupport.<Integer>component(name), 0);
    }
    private static boolean rootToggle(Minecraft client) {
        return client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireAttributeTestSupport.<Boolean>component("groundstomp_toggle"), true);
    }
    private static int boundKey(Minecraft client) throws ReflectiveOperationException {
        for (var binding : client.player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(
                JustDireAttributeTestSupport.<java.util.List<?>>component("ability_bindings"), java.util.List.of())) {
            if (binding.getClass().getMethod("abilityName").invoke(binding).equals("groundstomp")) {
                require((boolean) binding.getClass().getMethod("requireEquipped").invoke(binding)
                        && !(boolean) binding.getClass().getMethod("isMouse").invoke(binding), "Binding scope differs from server policy");
                return (int) binding.getClass().getMethod("key").invoke(binding);
            }
        }
        return -1;
    }
    private static Map<?, ?> map(Minecraft client, String name) { return (Map<?, ?>) JustDireSettingsSpikeClient.field(client.screen, name); }
    private static void click(Button widget, int button) { widget.onClick(event(widget.getX() + 8, widget.getY() + 8, button), false); }
    private static MouseButtonEvent event(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    private static void move(Minecraft client, double x, double y) throws ReflectiveOperationException {
        var window = client.getWindow();
        var method = client.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class); method.setAccessible(true);
        method.invoke(client.mouseHandler, window.handle(), x * window.getScreenWidth() / window.getGuiScaledWidth(), y * window.getScreenHeight() / window.getGuiScaledHeight());
        client.mouseHandler.handleAccumulatedMovement();
    }
    private static net.minecraft.server.level.ServerPlayer owner(Minecraft client) {
        var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID()); require(player != null, "Server owner missing"); return player;
    }
    private static void server(Minecraft client, Runnable action) { job = client.getSingleplayerServer().submit(action); }
    private static void add(String name, Action action) { add(name, action, () -> true); }
    private static void add(String name, Action action, Check done) { steps.add(new Step(name, action, done)); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    @FunctionalInterface private interface Check { boolean get() throws Exception; }
    private record Step(String name, Action start, Check done) {}
}
