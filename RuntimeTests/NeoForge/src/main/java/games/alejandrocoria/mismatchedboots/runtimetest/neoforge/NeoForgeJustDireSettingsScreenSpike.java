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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Real native screen/widgets, with an optional input/action connection; no production or manual UX claim. */
final class NeoForgeJustDireSettingsScreenSpike {
    private static final boolean INPUT = Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompInputSpike");
    private static final String LIFECYCLE = JustDireGroundStompLifecycleSpike.MODE;
    private static final String PREFIX = !LIFECYCLE.isEmpty() ? "just-dire-ground-stomp-lifecycle-" + LIFECYCLE
            : INPUT ? "just-dire-ground-stomp-input-spike" : "just-dire-settings-spike";
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
    private static int actionSequence;
    private static int pauseTick;
    private static JustDireGroundStompSpikeNetwork.Activate actionPacket;
    private static JustDireGroundStompSpikeNetwork.Activate heldAction;
    private static JustDireGroundStompSpikeNetwork.Activate oldAction;

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
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt", report + (!LIFECYCLE.isEmpty()
                        ? "test-only real world restart/native tier smithing with production state preservation; synthetic UI/input/real transport; no production action/manual acceptance\n" : INPUT
                        ? "test-only native UI -> synthetic event-bus key -> real mixed permits/action/ticking; no physical/manual, production, restart or smithing acceptance\n"
                        : "test-only; native widgets/callbacks, synthetic mouse movement/key events and real packets; no physical/manual acceptance or mixed activation\n"));
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
                finished = true;
            } else if (!started) {
                step.start.run(); started = true; wait = 0;
            } else if (step.done.get()) {
                report.append(step.name).append('\n'); steps.remove(); started = false;
            } else if (++wait > 600) { throw new AssertionError("Timed out: " + step.name + "; reply=" + JustDireSettingsSpikeClient.lastReply); }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(PREFIX, new AssertionError("Step: " + (steps.peek() == null ? "finish" : steps.peek().name), failure));
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
        if (!LIFECYCLE.isEmpty()) { lifecycle(client); return; }
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
        if (INPUT) { input(client); return; }
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

    private static void input(Minecraft client) {
        for (boolean inverse : new boolean[]{false, true}) {
            setup(client, true, inverse, false, 30_000);
            open(client); select(client, 36);
            edit(client, "native slider lower endpoint before action strength", () -> slider(client).setValue(1),
                    () -> rootValue(client, "groundstomp_value") == 1);
            edit(client, "UI strength 5 becomes the live action parameter", () -> slider(client).setValue(5),
                    () -> rootValue(client, "groundstomp_value") == 5);
            reveal(client); assignKey(client, GLFW.GLFW_KEY_G);
            add("open GUI suppresses the mixed keyboard listener", () -> {
                int count = JustDireGroundStompSpikeInput.sequence; key(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
                require(count == JustDireGroundStompSpikeInput.sequence, "GUI dispatched mixed input");
            });
            rejectPreparation(client, "server rejects forged preparation while native menu is open",
                    () -> new JustDireGroundStompSpikeNetwork.Prepare(++JustDireGroundStompSpikeInput.sequence, 71, false));
            close(client);
            prepareTarget(client);
            add("wrong key, release and repeat emit no mixed request", () -> {
                int count = JustDireGroundStompSpikeInput.sequence;
                key(GLFW.GLFW_KEY_H, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_G, GLFW.GLFW_RELEASE); key(GLFW.GLFW_KEY_G, GLFW.GLFW_REPEAT);
                require(count == JustDireGroundStompSpikeInput.sequence, "Unsupported input dispatched");
            });
            activation(client, "menu selection token is not an action permit", () -> sendAction(
                    new JustDireGroundStompSpikeNetwork.Activate(JustDireGroundStompSpikeInput.sequence,
                            new UUID(0, JustDireSettingsSpikeClient.token), 71, false)), false);
            if (!inverse) {
                rejectPreparation(client, "wire rejects mouse preparation", () ->
                        new JustDireGroundStompSpikeNetwork.Prepare(++JustDireGroundStompSpikeInput.sequence, 71, true));
                rejectPreparation(client, "wire rejects invalid keyboard value", () ->
                        new JustDireGroundStompSpikeNetwork.Prepare(++JustDireGroundStompSpikeInput.sequence, -2, false));
                prepareHeld(client, "genuine key preparation held for equal-root replacement");
                add("replace equipped root with an equal copy", () -> server(client, () -> {
                    var player = owner(client); player.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
                    player.inventoryMenu.broadcastChanges();
                }));
                activation(client, "equal root replacement rejects held activation", () -> sendAction(heldAction), false);
                prepareHeld(client, "genuine key preparation held for native UI binding removal");
                open(client); select(client, 36); reveal(client); assignKey(client, GLFW.GLFW_KEY_ESCAPE);
                close(client);
                activation(client, "UI binding removal invalidates the pending activation", () -> sendAction(heldAction), false);
                add("removed native UI binding stops normal client dispatch", () -> {
                    int count = JustDireGroundStompSpikeInput.sequence; key(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
                    require(count == JustDireGroundStompSpikeInput.sequence, "Removed binding dispatched");
                });
                rejectPreparation(client, "removed binding rejects forged preparation", () ->
                        new JustDireGroundStompSpikeNetwork.Prepare(++JustDireGroundStompSpikeInput.sequence, 71, false));
                open(client); select(client, 36); reveal(client); assignKey(client, GLFW.GLFW_KEY_G); close(client);
                add("disable only the prototype action route", () -> server(client, () -> JustDireGroundStompSpikeNetwork.available = false));
                rejectPreparation(client, "unavailable route rejects a genuine configured key", () -> {
                    press(); return null; // The listener, not the fixture, sends this request.
                });
                add("restore the prototype action route", () -> server(client, () -> JustDireGroundStompSpikeNetwork.available = true));
            }
            add("enable immediate prepared delivery", () -> JustDireGroundStompSpikeInput.deferActivation = false);
            activation(client, "mixed " + inverse + ": UI-assigned G causes motion 5, one 5000 FE bill and cooldown 200", NeoForgeJustDireSettingsScreenSpike::press, true);
            add("retain the exact genuine activation packet", () -> oldAction = actionPacket);
            activation(client, "immediate duplicate activation rejects unchanged", () -> sendAction(oldAction), false);
            rejectPreparation(client, "fresh configured key rejects during cooldown", () -> { press(); return null; });
            add("normal equipped ticks expire the live root cooldown", () -> {},
                    () -> NeoForgeJustDireGroundStompSpike.timer(client.player.getItemBySlot(EquipmentSlot.FEET)) == -1);
            activation(client, "replay still rejects after cooldown expiry", () -> sendAction(oldAction), false);
            rejectPreparation(client, "old preparation sequence cannot mint another permit", () ->
                    new JustDireGroundStompSpikeNetwork.Prepare(oldAction.sequence(), 71, false));
            if (!inverse) {
                prepareHeld(client, "first fresh prepared activation");
                add("retain superseded prepared activation", () -> oldAction = heldAction);
                prepareHeld(client, "newer configured key supersedes the old permit");
                activation(client, "superseded permit rejects before effects/payment", () -> sendAction(oldAction), false);
                activation(client, "latest genuine permit pays and applies the action once", () -> sendAction(heldAction), true);
            } else {
                activation(client, "fresh configured key after expiry pays and applies once", NeoForgeJustDireSettingsScreenSpike::press, true);
            }
            add("discard only the disposable effect target", () -> server(client, () -> {
                JustDireGroundStompSpikeNetwork.observedTarget.discard(); JustDireGroundStompSpikeNetwork.observedTarget = null;
            }));
        }
        add("native original retains its own key/packet path with no mixed prepare", () -> server(client, () -> {
            var player = owner(client); player.getInventory().clearContent();
            var boot = NeoForgeJustDireGroundStompSpike.boots("eclipsealloy", 6_000);
            JustDireAttributeTestSupport.set(boot, "groundstomp_bindingmode", 2);
            try {
                var type = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
                JustDireAttributeTestSupport.set(boot, "ability_bindings", java.util.List.of(
                        type.getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true)));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            player.setItemSlot(EquipmentSlot.FEET, boot); player.inventoryMenu.broadcastChanges();
        }), () -> !JustDireSettingsSpikeSupport.mixed(client.player.getItemBySlot(EquipmentSlot.FEET))
                && charge(client.player.getItemBySlot(EquipmentSlot.FEET)) == 6_000);
        add("native G uses native billing and cooldown without our input channel", () -> {
            JustDireGroundStompSpikeInput.deferActivation = false;
            int count = JustDireGroundStompSpikeInput.sequence; key(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
            require(count == JustDireGroundStompSpikeInput.sequence, "Native original entered mixed channel");
        }, () -> charge(client.player.getItemBySlot(EquipmentSlot.FEET)) == 1_000
                && NeoForgeJustDireGroundStompSpike.timer(client.player.getItemBySlot(EquipmentSlot.FEET)) > 0);
        add("native server original was billed exactly once", () -> server(client, () ->
                require(charge(owner(client).getItemBySlot(EquipmentSlot.FEET)) == 1_000, "Native original double bill")));
    }

    private static void assignKey(Minecraft client, int key) {
        edit(client, key == GLFW.GLFW_KEY_ESCAPE ? "native Escape removes the live binding" : "native capture assigns equipped keyboard G", () -> {
            click((Button) map(client, "bindingButtons").get(icon(client)), 0);
            require(client.screen.keyPressed(new KeyEvent(key, 0, 0)), "Native capture ignored key");
        }, () -> boundKey(client) == (key == GLFW.GLFW_KEY_ESCAPE ? -1 : key));
    }
    private static void key(int key, int action) { NeoForge.EVENT_BUS.post(new InputEvent.Key(new KeyEvent(key, 0, 0), action)); }
    private static void press() {
        actionSequence = JustDireGroundStompSpikeInput.sequence + 1; key(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
        require(JustDireGroundStompSpikeInput.sequence == actionSequence, "Configured key did not dispatch preparation");
    }
    private static void sendAction(JustDireGroundStompSpikeNetwork.Activate packet) {
        actionPacket = packet; actionSequence = packet.sequence(); ClientPacketDistributor.sendToServer(packet);
    }
    private static void resetTarget(Minecraft client) { add("reset disposable target before the next request", () -> server(client, () -> positionTarget(client))); }
    private static void positionTarget(Minecraft client) {
        var target = JustDireGroundStompSpikeNetwork.observedTarget;
        if (target == null) { return; }
        var player = owner(client);
        double offset = (player.getX() - Math.floor(player.getX() / 16) * 16) < 14 ? 2 : -2;
        target.setPos(player.getX() + offset, player.getY(), player.getZ()); target.setDeltaMovement(Vec3.ZERO);
    }
    private static void prepareHeld(Minecraft client, String name) {
        resetTarget(client);
        add(name, () -> { JustDireGroundStompSpikeInput.deferActivation = true; press(); }, () -> {
            var prepared = JustDireGroundStompSpikeInput.lastPrepared;
            if (prepared == null || prepared.sequence() != actionSequence) { return false; }
            require(!prepared.nonce().equals(JustDireGroundStompSpikeNetwork.NONE) && JustDireGroundStompSpikeInput.lastActivation != null,
                    "Actual key did not obtain a permit");
            heldAction = JustDireGroundStompSpikeInput.lastActivation; return true;
        });
    }
    private static void rejectPreparation(Minecraft client, String name, Supplier<JustDireGroundStompSpikeNetwork.Prepare> request) {
        resetTarget(client);
        add(name, () -> {
            before = client.player.getItemBySlot(EquipmentSlot.FEET).copy(); JustDireGroundStompSpikeInput.lastPrepared = null;
            var packet = request.get();
            if (packet != null) { actionSequence = packet.sequence(); ClientPacketDistributor.sendToServer(packet); }
        }, () -> {
            var reply = JustDireGroundStompSpikeInput.lastPrepared;
            if (reply == null || reply.sequence() != actionSequence) { return false; }
            require(reply.nonce().equals(JustDireGroundStompSpikeNetwork.NONE), "Rejected preparation issued a permit");
            require(charge(before) == charge(client.player.getItemBySlot(EquipmentSlot.FEET)), "Preparation billed root");
            server(client, () -> {
                require(charge(before) == charge(owner(client).getItemBySlot(EquipmentSlot.FEET)), "Rejected preparation billed server root");
                var target = JustDireGroundStompSpikeNetwork.observedTarget;
                require(target == null || target.getDeltaMovement().horizontalDistance() == 0, "Rejected preparation applied an effect");
            });
            return true;
        });
    }
    private static void activation(Minecraft client, String name, Action trigger, boolean accepted) {
        activation(client, name, trigger, accepted, 5);
    }
    private static void activation(Minecraft client, String name, Action trigger, boolean accepted, double strength) {
        resetTarget(client);
        add(name, () -> {
            before = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
            JustDireGroundStompSpikeInput.result = null; actionPacket = null; trigger.run();
        }, () -> {
            var result = JustDireGroundStompSpikeInput.result;
            if (result == null || result.sequence() != actionSequence) { return false; }
            if (actionPacket == null) { actionPacket = JustDireGroundStompSpikeInput.lastActivation; }
            var observed = JustDireGroundStompSpikeNetwork.observation;
            require(actionPacket != null && result.nonce().equals(actionPacket.nonce()) && observed != null
                    && observed.packet().equals(actionPacket) && observed.accepted() == accepted && result.accepted() == accepted,
                    "Unexpected action receipt/observation: " + result);
            require(charge(observed.before()) == charge(before) && observed.before().getDamageValue() == before.getDamageValue(),
                    "Input/preparation performed an extra bill before activation");
            if (accepted) {
                require(charge(observed.after()) == charge(observed.before()) - 5_000 && observed.cooldown() == 200
                        && observed.motionBefore() == 0 && Math.abs(observed.motionAfter() - strength) < .0001
                        && observed.before().getDamageValue() == observed.after().getDamageValue(), "Effect/single bill/timer mismatch: " + observed);
            } else {
                require(ItemStack.matches(observed.before(), observed.after()) && observed.motionBefore() == observed.motionAfter(),
                        "Rejected activation changed root or target");
            }
            var root = client.player.getItemBySlot(EquipmentSlot.FEET);
            require(charge(root) == charge(observed.after()) && root.getDamageValue() == observed.after().getDamageValue()
                    && sources.equals(ModContent.getMixedBootsData(root).orElseThrow())
                    && sources.equals(ModContent.getMixedBootsData(observed.after()).orElseThrow()), "Client billing/source synchronization mismatch");
            if (accepted) { require(NeoForgeJustDireGroundStompSpike.timer(root) > 0, "Client did not receive root cooldown"); }
            return true;
        });
    }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }

    private static void prepareTarget(Minecraft client) {
        add("prepare a real visible world target", () -> server(client, () -> {
            try { NeoForgeJustDireGroundStompSpike.initializeApi(); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            var player = owner(client);
            var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
            require(mob != null, "Missing input target");
            mob.setNoAi(true); mob.setNoGravity(true); mob.setInvulnerable(true);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            JustDireGroundStompSpikeNetwork.observedTarget = mob;
            positionTarget(client);
            require(player.level().addFreshEntity(mob)
                    && player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mob.getBoundingBox()).contains(mob),
                    "Target is not visible to the actual action query");
        }));
    }

    private static void lifecycle(Minecraft client) {
        if (LIFECYCLE.equals("restart")) {
            add("load disk roots in a new JVM without rebuilding/replacing the world", () -> server(client, () -> {
                try {
                    NeoForgeJustDireGroundStompSpike.initializeApi();
                    JustDireGroundStompLifecycleSpike.verifyDisk(owner(client));
                } catch (Exception failure) { throw new AssertionError(failure); }
            }));
        } else { require(LIFECYCLE.equals("save"), "Unknown lifecycle mode"); }
        for (int orientation = 0; orientation < 2; orientation++) {
            int slot = orientation;
            if (LIFECYCLE.equals("save")) {
                setup(client, true, slot == 1, false, 10_000, "celestigem");
                add("seed full valid root FE and ordinary wear for lifecycle observation", () -> server(client, () -> {
                    var player = owner(client);
                    player.getItemBySlot(EquipmentSlot.FEET).set(JustDireResourceTestSupport.energyComponent(), 10_000);
                    player.getItemBySlot(EquipmentSlot.FEET).setDamageValue(31);
                    player.inventoryMenu.broadcastChanges();
                }), () -> charge(client.player.getItemBySlot(EquipmentSlot.FEET)) == 10_000
                        && client.player.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 31);
                open(client); select(client, 36);
                edit(client, "native UI lower strength before persisted setting", () -> slider(client).setValue(1),
                        () -> rootValue(client, "groundstomp_value") == 1);
                edit(client, "native UI strength 3 is the persisted live control", () -> slider(client).setValue(3),
                        () -> rootValue(client, "groundstomp_value") == 3);
                reveal(client);
                edit(client, "native particle control is persisted independently of source history",
                        () -> click((Button) map(client, "customSettingsButtons").get(icon(client)), 0),
                        () -> rootValue(client, "groundstomp_custom_setting") == 1);
                assignKey(client, GLFW.GLFW_KEY_G); close(client);
                prepareTarget(client);
                activation(client, "configured Celestigem orientation " + slot + " spends once before world save",
                        NeoForgeJustDireSettingsScreenSpike::press, true, 3);
                add("unequip and store the actual spent root/timer in the world", () -> server(client, () -> {
                    var player = owner(client);
                    var root = player.getItemBySlot(EquipmentSlot.FEET);
                    try { require(NeoForgeJustDireGroundStompSpike.timer(root) > 0 && charge(root) == 5_000, "No live spent timer to save"); }
                    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    JustDireGroundStompLifecycleSpike.save(player, slot, root, actionPacket);
                    JustDireGroundStompSpikeNetwork.observedTarget.discard();
                    JustDireGroundStompSpikeNetwork.observedTarget = null;
                    player.inventoryMenu.broadcastChanges();
                }), () -> client.player.getItemBySlot(EquipmentSlot.FEET).isEmpty());
            } else {
                add("equip disk-loaded orientation " + slot + " with its remaining timer", () -> {
                    oldAction = JustDireGroundStompLifecycleSpike.oldPacket(slot);
                    server(client, () -> {
                        var player = owner(client); player.getInventory().clearContent();
                        var root = JustDireGroundStompLifecycleSpike.storage(player, false).removeItemNoUpdate(slot);
                        sources = ModContent.getMixedBootsData(root).orElseThrow();
                        player.setItemSlot(EquipmentSlot.FEET, root);
                        player.inventoryMenu.broadcastChanges();
                    });
                }, () -> ModContent.getMixedBootsData(client.player.getItemBySlot(EquipmentSlot.FEET)).isPresent()
                        && charge(client.player.getItemBySlot(EquipmentSlot.FEET)) == 5_000);
                prepareTarget(client);
                rejectPreparation(client, "saved positive cooldown still blocks a genuine configured G", () -> { press(); return null; });
                add("pause worn cooldown and exercise actual tier preview/take plus preservation boundaries", () -> server(client, () -> {
                    try {
                        var player = owner(client); var root = player.getItemBySlot(EquipmentSlot.FEET);
                        require(NeoForgeJustDireGroundStompSpike.timer(root) > 0, "Timer expired before smithing");
                        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        report.append("orientation ").append(slot).append(": ")
                                .append(JustDireGroundStompLifecycleSpike.boundaryMatrix(player, root)).append('\n');
                        player.getInventory().clearContent();
                        var upgraded = JustDireGroundStompLifecycleSpike.smithing(player, root, slot == 1);
                        player.getInventory().clearContent();
                        sources = ModContent.getMixedBootsData(upgraded).orElseThrow();
                        player.setItemSlot(EquipmentSlot.FEET, upgraded);
                        player.inventoryMenu.broadcastChanges();
                    } catch (Exception failure) { throw new AssertionError(failure); }
                }), () -> JustDireResourceTestSupport.handler(client.player.getItemBySlot(EquipmentSlot.FEET)).getCapacityAsInt() == 500_000);
                open(client); select(client, 36);
                add("native screen displays the preserved binding and strength after tier upgrade", () ->
                        require(boundKey(client) == 71 && slider(client).getValueInt() == 3, "Tier UI reset live controls"));
                close(client);
                activation(client, "old JVM permit cannot activate the upgraded root", () -> sendAction(oldAction), false);
                add("re-equipped saved cooldown expires naturally instead of resetting", () -> {},
                        () -> NeoForgeJustDireGroundStompSpike.timer(client.player.getItemBySlot(EquipmentSlot.FEET)) == -1);
                activation(client, "old JVM replay still rejects after saved cooldown expiry", () -> sendAction(oldAction), false);
                activation(client, "fresh persisted G uses upgraded boots, unchanged strength and one root bill",
                        NeoForgeJustDireSettingsScreenSpike::press, true, 3);
                add("discard only the disposable effect target", () -> server(client, () -> {
                    JustDireGroundStompSpikeNetwork.observedTarget.discard(); JustDireGroundStompSpikeNetwork.observedTarget = null;
                }));
            }
        }
        if (LIFECYCLE.equals("save")) {
            add("stored unequipped cooldowns pause through ten normal server ticks",
                    () -> server(client, () -> pauseTick = client.getSingleplayerServer().getTickCount()), () -> {
                if (client.getSingleplayerServer().getTickCount() - pauseTick < 10) { return false; }
                server(client, () -> {
                    try { JustDireGroundStompLifecycleSpike.verifyDisk(owner(client)); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                });
                return true;
            });
        }
    }

    private static void setup(Minecraft client, boolean mixed, boolean inverse) {
        setup(client, mixed, inverse, false);
    }
    private static void setup(Minecraft client, boolean mixed, boolean inverse, boolean pairedTier) {
        setup(client, mixed, inverse, pairedTier, 0);
    }
    private static void setup(Minecraft client, boolean mixed, boolean inverse, boolean pairedTier, int sourceCharge) {
        setup(client, mixed, inverse, pairedTier, sourceCharge, "eclipsealloy");
    }
    private static void setup(Minecraft client, boolean mixed, boolean inverse, boolean pairedTier, int sourceCharge, String tier) {
        add("prepare " + (mixed ? "mixed " + inverse : "native original"), () -> {
            var boot = NeoForgeJustDireGroundStompSpike.boots(tier, sourceCharge);
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
