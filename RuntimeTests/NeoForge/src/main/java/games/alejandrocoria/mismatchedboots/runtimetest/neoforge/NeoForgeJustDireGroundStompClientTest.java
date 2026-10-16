package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireAbilityInput;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsScreenAccess;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCredits;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native widgets/capture and production client listeners/transport, not a second UI or action adapter. */
final class NeoForgeJustDireGroundStompClientTest {
    private static final String PREFIX = "just-dire-ground-stomp-client";
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompClientVariant", "live");
    private static final String LIFECYCLE = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompLifecycle", "");
    private static final String OUTPUT = LIFECYCLE.isEmpty() ? PREFIX : "just-dire-ground-stomp-lifecycle-" + LIFECYCLE;
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static Future<?> job;
    private static boolean initialized, started, finished;
    private static int wait;
    private static ItemStack expected, before;
    private static JustDireRefundCredits lifecycleRefundCredits;
    private static Object provenance;
    private static JustDireActiveNetwork.SettingsRequest stale;
    private static double enabledStep;
    private static boolean holdReply;
    private static JustDireActiveNetwork.ActivationReply observedActivation;
    private static JustDireActiveNetwork.Activate consumed, held;
    private static Mob target;
    private static Object previousServer, previousPlayer;
    private static ItemStack inputRoot;
    private static JsonElement reconnectExpected;
    private static int earthquakeKey;
    private static boolean earthquakeMouse;
    private static Button earthquakeWidget;
    private static JustDireActiveNetwork.Activate savedEarthquakePermit;
    private static List<String> reconnectPassives;
    private static ItemStack pausedFeedbackRoot;
    private static ItemStack lavaBefore;
    private static double depletedWearArmor;

    private NeoForgeJustDireGroundStompClientTest() {}

    static void tick(Minecraft client) {
        if (finished || !initialized && !joined(client)) { return; }
        try {
            if (!initialized) { initialize(client); initialized = true; }
            if (job != null) { if (!job.isDone()) { return; } job.get(); job = null; }
            Step step = steps.peek();
            if (step == null) {
                // Let final close/ordinary slot packets settle before stopping the local channel.
                if (++wait <= 20) { return; }
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report + "production native UI/client keyboard/mouse/server transport; synthetic events, no physical/manual acceptance\n");
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", LIFECYCLE.isEmpty() ? VARIANT + "\n" : "neoforge\n"); finished = true;
            } else if (!started) { step.start.run(); started = true; wait = 0; }
            else if (step.done.getAsBoolean()) { report.append(step.name).append('\n'); steps.remove(); started = false; wait = 0; }
            else if (++wait > step.timeout) { throw new AssertionError("Timed out: " + step.name
                    + "; charge=" + charge(feet(client)) + ", timer=" + timer(feet(client))
                    + "; activation=" + observedActivation + "; pending=" + fieldStatic(JustDireAbilityInput.class, "pending")
                    + "; changed components=" + changedComponents(before, feet(client))); }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, new AssertionError("Step: " + (steps.peek() == null ? "finish" : steps.peek().name), failure)); finished = true; }
        if (finished) { client.stop(); }
    }

    private static void initialize(Minecraft client) {
        require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Client not hidden/muted");
        if (VARIANT.equals("absent")) {
            add("wait for absent-JDT client to finish joining", () -> {}, () -> client.screen == null);
            add("quiet optional absence leaves keyboard listener safe", () -> {
                require(!JustDireSettingsCompatibility.available(), "Absent API available");
                press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
                mousePress(3, GLFW.GLFW_PRESS);
                require(client.screen == null, "Absent input opened screen");
            });
            return;
        }
        add("native settings and global toggle keys assigned temporarily", () -> {
            key("toolUI").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_O));
            key("toggleTool").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));
            KeyMapping.resetMapping();
        });
        if (!LIFECYCLE.isEmpty()) { lifecycle(client); return; }
        if (VARIANT.equals("lava") || VARIANT.equals("lavaSettingsFailure")) { lavaControls(client); return; }
        if (VARIANT.equals("hooksMissing") || VARIANT.equals("apiMissing")) {
            unavailable(client); return;
        }
        if (VARIANT.equals("earthquakeMissing") || VARIANT.equals("earthquakeSettingsFailure")) {
            earthquakeUnavailable(client); return;
        }
        setup(client, false, false, 6_000);
        add("native original keyboard retains one native action", () -> press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS), () -> charge(feet(client)) == 1_000);
        add("move original to main hand for native selection control", () -> server(client, player -> {
            var original = boot("eclipsealloy", 0); player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, original); player.inventoryMenu.broadcastChanges();
        }), () -> !client.player.getMainHandItem().isEmpty() && feet(client).isEmpty());
        open(client);
        add("native original selection and controls remain native", () -> require(!maps(client, "sliders").isEmpty()
                && !JustDireSettingsCompatibility.mixed((ItemStack) field(client.screen, "tool")), "Native controls missing"));
        add("native original toggle callback still uses native packet", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> !flag(client.player.getMainHandItem(), "groundstomp_toggle", true));
        add("native original toggle re-enables before mouse control", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> flag(client.player.getMainHandItem(), "groundstomp_toggle", true));
        reveal(client, "GROUNDSTOMP");
        add("native original retains inventory mouse capture", () -> {
            click((Button) maps(client, "requireEquippedButtons").get(icon(client, "GROUNDSTOMP")), 0);
            click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
            require(client.screen.mouseClicked(mouse(1, 1, 3), false), "Native mouse capture rejected");
        }, () -> bound(client.player.getMainHandItem()) == 3 && mouseBound(client.player.getMainHandItem()));
        close(client);
        add("recharge native original without changing its mouse route", () -> server(client, player -> {
            set(player.getMainHandItem(), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
        }), () -> charge(client.player.getMainHandItem()) == 6_000);
        add("native original inventory mouse retains one native action", () -> mousePress(3, GLFW.GLFW_PRESS),
                () -> charge(client.player.getMainHandItem()) == 1_000 && timer(client.player.getMainHandItem()) > 0);
        open(client);
        for (String name : List.of("STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE")) {
            reveal(client, name);
            add("native original " + name + " captures a shared passive mouse binding", () -> {
                click((Button) maps(client, "requireEquippedButtons").get(icon(client, name)), 0);
                click((Button) maps(client, "bindingButtons").get(icon(client, name)), 0);
                require(client.screen.mouseClicked(mouse(1, 1, 4), false), "Native passive capture rejected");
            }, () -> bindingFor(client.player.getMainHandItem(), name.toLowerCase(java.util.Locale.ROOT), 4, true));
        }
        close(client);
        add("native shared passive input remains eligible with tool off and zero FE", () -> server(client, player -> {
            var root = player.getMainHandItem(); set(root, "tool_enabled", false); set(root, "forge_energy", 0);
            player.inventoryMenu.broadcastChanges();
        }), () -> !flag(client.player.getMainHandItem(), "tool_enabled", true) && charge(client.player.getMainHandItem()) == 0);
        add("native shared input turns all three passives off", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> !flag(client.player.getMainHandItem(), name + "_toggle", true)));
        add("native shared input re-enables all three without FE or global enable", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> flag(client.player.getMainHandItem(), name + "_toggle", false))
                        && charge(client.player.getMainHandItem()) == 0 && !flag(client.player.getMainHandItem(), "tool_enabled", true));
        add("empty hands open native menu without internal-slot failure", () -> server(client, player -> {
            player.getInventory().clearContent(); player.inventoryMenu.broadcastChanges();
        }), () -> client.player.getInventory().isEmpty());
        open(client);
        add("empty selection and native internal-slot refresh are unchanged", () -> {
            require(((ItemStack) field(client.screen, "tool")).isEmpty(), "Empty selected a tool");
            var menu = ((AbstractContainerScreen<?>) client.screen).getMenu();
            assertInternalSlots(menu);
            server(client, player -> assertInternalSlots(player.containerMenu));
        });
        close(client);
        for (boolean inverse : new boolean[]{false, true}) {
            setup(client, true, inverse, 0); open(client); select(client, 36);
            add("mixed " + inverse + " exposes supported installed native widgets at zero FE", () -> {
                require(((JustDireSettingsScreenAccess) client.screen).mismatchedboots$jdtReady(), "Final local hooks unavailable");
                if (VARIANT.equals("high") || VARIANT.equals("low")) {
                    String hook = VARIANT.equals("high") ? "fixtureHigh" : "fixtureLow";
                    require(java.util.Arrays.stream(client.screen.getClass().getDeclaredMethods()).anyMatch(m -> m.getName().contains(hook)), "Competing wrapper was not applied");
                }
                var names = maps(client, "buttonToAbilityMap").values().stream().map(Object::toString).toList();
                require(names.size() == 6 && names.containsAll(List.of("GROUNDSTOMP", "STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE", "EARTHQUAKE", "LAVAREPAIR")), "Unsupported/missing abilities: " + names);
                require(maps(client, "sliders").size() == 2 && ((Number) field(slider(client, "GROUNDSTOMP"), "maxValue")).doubleValue() == 5
                        && ((Number) field(slider(client, "JUMPBOOST"), "maxValue")).doubleValue() == 5 && charge(feet(client)) == 0, "Per-ability tier ranges wrong");
                require(maps(client, "bindingButtons").size() == 6 && slider(client, "EARTHQUAKE") == null
                        && slider(client, "LAVAREPAIR") == null, "Custom bindings or intrinsic/fixed controls wrong");
            });
            for (String ability : List.of("STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE", "GROUNDSTOMP")) {
                String path = ability.toLowerCase(java.util.Locale.ROOT) + "_toggle";
                add("native " + ability + " toggle off", () -> click(icon(client, ability), 0), () -> !flag(feet(client), path, true));
                add("native " + ability + " toggle on", () -> click(icon(client, ability), 0), () -> flag(feet(client), path, true));
            }
            add("rapid native slider edits preserve widget identity and final root value", () -> {
                var slider = slider(client, "GROUNDSTOMP"); before = feet(client).copy();
                slider.setValue(1); slider.setValue(4); slider.setValue(2); slider.setValue(5);
                require(slider(client, "GROUNDSTOMP") == slider, "Slider rebuilt during edit");
                slider(client, "JUMPBOOST").setValue(1); slider(client, "JUMPBOOST").setValue(2);
            }, () -> value(feet(client), "groundstomp_value") == 5 && value(feet(client), "jumpboost_value") == 2);
            add("native toggle key disables the selected mixed root", () -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)),
                    () -> !flag(feet(client), "tool_enabled", true));
            add("native toggle key re-enables selected root", () -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)),
                    () -> flag(feet(client), "tool_enabled", true));
            reveal(client, "GROUNDSTOMP");
            add("mixed custom-only and equipped-only native controls visibly disabled", () -> {
                var icon = icon(client, "GROUNDSTOMP");
                require(!((Button) maps(client, "leftRightClickButtons").get(icon)).active
                        && !((Button) maps(client, "requireEquippedButtons").get(icon)).active, "Unsupported choices remain active");
            });
            add("native particle control edits live root", () -> click((Button) maps(client, "customSettingsButtons").get(icon(client, "GROUNDSTOMP")), 0),
                    () -> value(feet(client), "groundstomp_custom_setting") == 1);
            add("ordinary mouse clicks outside tool slots do not assign a binding", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                client.screen.mouseClicked(mouse(1, 1, 0), false);
                client.screen.mouseClicked(mouse(1, 1, 1), false);
                require(client.screen.mouseClicked(mouse(1, 1, 8), false), "Invalid additional capture button was not consumed");
                require((boolean) field(client.screen, "bindingEnabled"), "Mouse ended keyboard capture");
                require(client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)), "Keyboard capture failed");
            }, () -> bound(feet(client)) == GLFW.GLFW_KEY_H && flag(feet(client), "tool_enabled", true));
            add("native Escape capture removes the binding", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                require(client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)), "Escape not consumed");
            }, () -> bound(feet(client)) == -1);
            add("native capture assigns G for equipped action", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                require(client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0)), "G not captured");
            }, () -> bound(feet(client)) == GLFW.GLFW_KEY_G);
            add("GUI blocks production action despite the assigned key", () -> { before = feet(client).copy(); press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS); },
                    () -> wait > 12 && ItemStack.matches(before, feet(client)));
            close(client); open(client); select(client, 36);
            add("reopen retains actual root, tier values, particles and binding", () -> require(slider(client, "GROUNDSTOMP").getValueInt() == 5
                    && slider(client, "JUMPBOOST").getValueInt() == 2 && bound(feet(client)) == GLFW.GLFW_KEY_G
                    && value(feet(client), "groundstomp_custom_setting") == 1, "Reopen lost controls"));
            close(client);
            add("release/repeat/wrong key are not action presses", () -> {
                before = feet(client).copy(); press(GLFW.GLFW_KEY_G, GLFW.GLFW_RELEASE);
                press(GLFW.GLFW_KEY_G, GLFW.GLFW_REPEAT); press(GLFW.GLFW_KEY_Z, GLFW.GLFW_PRESS);
            }, () -> wait > 12 && ItemStack.matches(before, feet(client)));
            add("zero FE falls back once to ordinary durability without rewriting the slider", () -> press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS),
                    () -> timer(feet(client)) > 0 && feet(client).getDamageValue() == before.getDamageValue() + 25
                            && charge(feet(client)) == 0 && value(feet(client), "groundstomp_value") == 5);
            add("recharge and natural cooldown expiry retain root controls", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
            }), () -> charge(feet(client)) == 6_000 && timer(feet(client)) == -1);
            add("event-bus key reaches production prepare/reply/action exactly once", () -> {
                before = feet(client).copy(); press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
            }, () -> charge(feet(client)) == 1_000 && timer(feet(client)) > 0 && value(feet(client), "groundstomp_value") == 5
                    && feet(client).getDamageValue() == before.getDamageValue());
            add("cooldown rejects duplicate key without a second bill", () -> press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS),
                    () -> wait > 12 && charge(feet(client)) == 1_000);
            add("UI and action leave source provenance unchanged", () -> server(client, player -> require(
                    ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "Sources changed")));
            open(client); select(client, 36); reveal(client, "GROUNDSTOMP");
            add("native capture assigns additional mouse button without custom widgets", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                require(client.screen.mouseClicked(mouse(1, 1, 3), false)
                        && !(boolean) field(client.screen, "bindingEnabled"), "Native mouse capture did not finish");
            }, () -> JustDireSettingsCompatibility.groundStompBinding(feet(client), 3, true));
            add("mouse GUI denial starts with sufficient resources and expired cooldown", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
            }), () -> charge(feet(client)) == 6_000 && timer(feet(client)) == -1);
            add("GUI rejects assigned mouse input", () -> { before = feet(client).copy(); mousePress(3, GLFW.GLFW_PRESS); },
                    () -> wait > 12 && ItemStack.matches(before, feet(client)));
            close(client); open(client); select(client, 36);
            add("reopen shows the preserved native mouse binding", () -> {
                var binding = maps(client, "bindingMap").get(maps(client, "leftRightClickButtons").get(icon(client, "GROUNDSTOMP")));
                require(binding != null && bound(feet(client)) == 3 && mouseBound(feet(client))
                        && (boolean) fieldAccessor(binding, "isMouse"), "Native mouse preference lost on reopen");
            });
            close(client);
            add("mouse release/repeat/wrong button and keyboard numeric code do not activate", () -> {
                before = feet(client).copy(); mousePress(3, GLFW.GLFW_RELEASE); mousePress(3, GLFW.GLFW_REPEAT);
                for (int button : new int[]{0, 1, 2, 8}) { mousePress(button, GLFW.GLFW_PRESS); }
                press(3, GLFW.GLFW_PRESS);
            }, () -> wait > 12 && ItemStack.matches(before, feet(client)));
            add("event-bus mouse reaches production action with one root bill and unchanged sources", () -> {
                before = feet(client).copy(); mousePress(3, GLFW.GLFW_PRESS);
            }, () -> charge(feet(client)) == 1_000 && timer(feet(client)) > 0
                    && feet(client).getDamageValue() == before.getDamageValue()
                    && ModContent.getMixedBootsData(feet(client)).orElseThrow().equals(provenance));
            add("mouse cooldown rejects a second bill", () -> mousePress(3, GLFW.GLFW_PRESS),
                    () -> wait > 12 && charge(feet(client)) == 1_000);
            open(client); select(client, 36); reveal(client, "GROUNDSTOMP");
            add("native Escape removes a mouse binding", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            }, () -> bound(feet(client)) == -1);
            close(client);
            add("removed mouse binding stays inactive with sufficient resources and expired cooldown", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
            }), () -> charge(feet(client)) == 6_000 && timer(feet(client)) == -1);
            add("removed mouse binding cannot prepare a fresh action", () -> { before = feet(client).copy(); mousePress(3, GLFW.GLFW_PRESS); },
                    () -> wait > 12 && ItemStack.matches(before, feet(client)));
            passiveBindings(client);
        }
        setup(client, true, false, 6_000);
        for (String preference : List.of("unbound", "invalidMouse", "inventory", "wrongMode")) {
            add("prepare unsupported imported " + preference + " preference", () -> server(client, player -> {
                var root = player.getItemBySlot(EquipmentSlot.FEET);
                bind(root, preference.equals("unbound") ? -1 : GLFW.GLFW_KEY_G, preference.equals("invalidMouse"), !preference.equals("inventory"));
                if (preference.equals("wrongMode")) { set(root, "groundstomp_bindingmode", 1); }
                player.inventoryMenu.broadcastChanges(); expected = root.copy();
            }), () -> ItemStack.matches(expected, feet(client)));
            add("imported " + preference + " cannot authorize production input", () -> {
                before = feet(client).copy(); press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
            }, () -> wait > 12 && ItemStack.matches(before, feet(client)));
        }
        setup(client, true, false, 6_000); open(client); select(client, 36);
        add("put a distinct mixed root in the inventory", () -> server(client, player -> {
            var other = MixedBootsFactory.createPair(boot("blazegold", 0), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            set(other, "groundstomp_value", 1); bind(other, GLFW.GLFW_KEY_J, false, true); player.getInventory().setItem(9, other);
            player.inventoryMenu.broadcastChanges(); expected = other.copy(); before = player.getItemBySlot(EquipmentSlot.FEET).copy();
        }), () -> ItemStack.matches(expected, client.player.getInventory().getItem(9)));
        reveal(client, "GROUNDSTOMP");
        add("start mixed capture before selecting another mixed root", () -> {
            click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
            require((boolean) field(client.screen, "bindingEnabled"), "Mixed capture did not start");
        });
        select(client, 9);
        add("native tool switch uses the new root and its own tier range", () -> {
            require(!(boolean) field(client.screen, "bindingEnabled"), "Mixed selection did not cancel old capture");
            require(ItemStack.matches(expected, client.player.getInventory().getItem(9)) && ItemStack.matches(before, feet(client)),
                    "Capture selection changed either root or assigned an ordinary click");
            require(((JustDireSettingsScreenAccess) client.screen).mismatchedboots$jdtTool() == client.player.getInventory().getItem(9), "Selection is not actual root");
            require(((Number) field(slider(client, "GROUNDSTOMP"), "maxValue")).doubleValue() == 2, "Old tier range retained");
            slider(client, "GROUNDSTOMP").setValue(2);
        }, () -> value(client.player.getInventory().getItem(9), "groundstomp_value") == 2 && ItemStack.matches(before, feet(client)));
        add("prepare native inventory tool and capture old mixed token", () -> {
            stale = new JustDireActiveNetwork.SettingsRequest(((AbstractContainerScreen<?>) client.screen).getMenu().containerId, 9,
                    (int) fieldStatic(JustDireSettingsClient.class, "selection"), (int) fieldStatic(JustDireSettingsClient.class, "request") + 100,
                    (UUID) fieldStatic(JustDireSettingsClient.class, "token"), JustDireActiveNetwork.TOGGLE, "groundstomp", 0, -1, false, true);
            server(client, player -> { player.getInventory().setItem(10, boot("eclipsealloy", 0)); player.inventoryMenu.broadcastChanges(); });
        }, () -> !client.player.getInventory().getItem(10).isEmpty());
        reveal(client, "GROUNDSTOMP");
        add("start mixed capture before selecting a native original", () -> {
            expected = client.player.getInventory().getItem(9).copy();
            click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
            require((boolean) field(client.screen, "bindingEnabled"), "Second mixed capture did not start");
        });
        select(client, 10, false, 1);
        add("right-click native selection cancels mixed capture without assigning either binding", () -> require(
                !(boolean) field(client.screen, "bindingEnabled")
                        && ItemStack.matches(expected, client.player.getInventory().getItem(9))
                        && ItemStack.matches(boot("eclipsealloy", 0), client.player.getInventory().getItem(10)),
                "Native selection retained capture or changed a binding"));
        add("native selection drops mixed token through production deselection transport", () -> {}, () -> wait > 12);
        add("old mixed token cannot edit after switching to a native original", () -> server(client, player -> require(
                !JustDireActiveNetwork.edit(player, stale).accepted(), "Old mixed selection survived native switch")));
        select(client, 9);
        add("selected slot removal clears stale native widgets", () -> server(client, player -> {
            player.getInventory().setItem(9, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges();
        }), () -> ((ItemStack) field(client.screen, "tool")).isEmpty() && maps(client, "buttonToAbilityMap").isEmpty());
        select(client, 36);
        add("equipped selection remains usable after another slot disappeared", () -> require(
                ((Number) field(slider(client, "GROUNDSTOMP"), "maxValue")).doubleValue() == 5, "Reselection lost equipped tier"));
        add("equal-state server root replacement invalidates the selected identity", () -> server(client, player -> {
            before = player.getItemBySlot(EquipmentSlot.FEET).copy();
            player.setItemSlot(EquipmentSlot.FEET, before.copy()); player.inventoryMenu.broadcastChanges();
        }), () -> wait > 12 && ItemStack.matches(before, feet(client)));
        add("stale native callback cannot edit and clears unusable controls", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> ((ItemStack) field(client.screen, "tool")).isEmpty() && maps(client, "buttonToAbilityMap").isEmpty()
                        && ItemStack.matches(before, feet(client)));
        select(client, 36);
        add("fresh native reselection can edit the replacement root", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> !flag(feet(client), "groundstomp_toggle", true));
        close(client);
        earthquake(client);
        if (VARIANT.equals("live")) { poweredJump(client); }
    }

    private static void lavaControls(Minecraft client) {
        if (VARIANT.equals("lavaSettingsFailure")) {
            setup(client, true, false, 0); open(client); select(client, 36);
            add("unsupported Lava metadata hides only Lava controls/input, not gameplay or root tooltip", () -> {
                require(!hasIcon(client, "LAVAREPAIR") && hasIcon(client, "JUMPBOOST") && hasIcon(client, "EARTHQUAKE")
                        && JustDireSettingsCompatibility.metadata(feet(client), "lavarepair") == null
                        && JustDireLavaRepairCompatibility.eligible(feet(client)), "Lava settings fault leaked into unrelated readiness");
                lavaTooltip(client, true);
            });
            add("older native settings still edit after Lava metadata failure", () -> click(icon(client, "JUMPBOOST"), 0),
                    () -> !flag(feet(client), "jumpboost_toggle", true));
            close(client);
            add("Lava settings failure does not disable checked ritual", () -> server(client, player -> lavaDrop(player, true)));
            return;
        }
        for (boolean twice : new boolean[]{false, true}) {
            for (boolean inverse : new boolean[]{false, true}) {
                add("prepare intrinsic Lava " + twice + "/" + inverse + " without any installed upgrade", () -> server(client, player -> {
                    var source = NeoForgeJustDireResourcesTest.boots("blazegold", 0);
                    set(source, "lavarepair_toggle", false); // Frozen source switches must not govern root.
                    var pair = MixedBootsFactory.createPair(source, twice ? source.copy() : new ItemStack(Items.DIAMOND_BOOTS));
                    expected = inverse ? pair.inverse() : pair.primary();
                    // Initial projection is allowed to import preferences. Exercise live absent-root
                    // defaults independently of that creation policy and the frozen false sources.
                    expected.remove(JustDireAttributeTestSupport.component("lavarepair_toggle"));
                    expected.remove(JustDireAttributeTestSupport.component("tool_enabled"));
                    provenance = ModContent.getMixedBootsData(expected).orElseThrow();
                    player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                    client.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
                    player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
                }), () -> ItemStack.matches(expected, feet(client)));
                open(client); select(client, 36);
                add("native intrinsic widget has no slider/installation requirement; absence defaults on", () -> {
                    require(maps(client, "buttonToAbilityMap").size() == 1 && hasIcon(client, "LAVAREPAIR")
                            && slider(client, "LAVAREPAIR") == null && maps(client, "bindingButtons").size() == 1,
                            "Intrinsic native widget inventory wrong");
                    require(!feet(client).has(JustDireAttributeTestSupport.component("lavarepair_toggle")), "Native selection materialized absent root preference");
                    lavaTooltip(client, true);
                });
                add("native Lava click writes explicit false at root", () -> click(icon(client, "LAVAREPAIR"), 0),
                        () -> !flag(feet(client), "lavarepair_toggle", true));
                add("disabled tooltip follows root while capability stays fireproof", () -> {
                    lavaTooltip(client, false);
                    require(JustDireLavaRepairCompatibility.eligible(feet(client)), "Toggle removed intrinsic protection");
                });
                assign(client, "LAVAREPAIR", GLFW.GLFW_KEY_V, false);
                close(client);
                add("disabled root survives actual lava ticks without consuming source", () -> server(client, player -> lavaDrop(player, false)));
                add("configured equipped key freely enables intrinsic Lava once", () -> press(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS),
                        () -> flag(feet(client), "lavarepair_toggle", false));
                add("enabled root repairs despite frozen source switches", () -> server(client, player -> lavaDrop(player, true)));
                open(client); select(client, 36); assign(client, "LAVAREPAIR", 4, true);
                add("native global toggle disables ritual but not intrinsic capability", () -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)),
                        () -> !flag(feet(client), "tool_enabled", true));
                add("global-off tooltip is disabled", () -> lavaTooltip(client, false));
                close(client);
                add("global-off root remains protected without ritual", () -> server(client, player -> lavaDrop(player, false)));
                add("equipped extra mouse toggles Lava even with global tool off and no FE", () -> mousePress(4, GLFW.GLFW_PRESS),
                        () -> !flag(feet(client), "lavarepair_toggle", true));
                add("same extra mouse freely re-enables Lava exactly once", () -> mousePress(4, GLFW.GLFW_PRESS),
                        () -> flag(feet(client), "lavarepair_toggle", false));
                add("intrinsic controls never mutate provenance or install a sixth upgrade", () -> require(
                        ModContent.getMixedBootsData(feet(client)).orElseThrow().equals(provenance)
                                && charge(feet(client)) == 0, "Intrinsic toggles changed history/resources"));
                open(client); select(client, 36);
                add("Lava rejects unused numeric/particle and inventory/ordinary-mouse edits", () -> server(client, player -> {
                    var root = player.getItemBySlot(EquipmentSlot.FEET); var snapshot = root.copy();
                    int selection = (int) fieldStatic(JustDireSettingsClient.class, "selection");
                    int request = (int) fieldStatic(JustDireSettingsClient.class, "request") + 100;
                    UUID token = (UUID) fieldStatic(JustDireSettingsClient.class, "token");
                    for (int operation : List.of(JustDireActiveNetwork.VALUE, JustDireActiveNetwork.PARTICLES, JustDireActiveNetwork.BINDING)) {
                        require(!JustDireActiveNetwork.edit(player, new JustDireActiveNetwork.SettingsRequest(player.containerMenu.containerId, 36,
                                selection, request++, token, operation, "lavarepair", 2, 0, true, false)).accepted(), "Unsupported Lava edit accepted");
                    }
                    require(ItemStack.matches(snapshot, root), "Rejected Lava edits mutated root");
                }));
                close(client);
                add("recomposition leaves Lava preferences dormant without eligibility", () -> server(client, player -> {
                    var root = player.getItemBySlot(EquipmentSlot.FEET);
                    var dormant = MixedBootsRecomposer.recompose(root, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
                    require(!JustDireLavaRepairCompatibility.eligible(dormant)
                            && JustDireSettingsCompatibility.metadata(dormant, "lavarepair") == null
                            && JustDireSettingsCompatibility.boundPassives(dormant, 4, true).isEmpty(), "Dormant preference granted Lava");
                    var restored = MixedBootsRecomposer.recompose(dormant, NeoForgeJustDireResourcesTest.boots("blazegold", 0), new ItemStack(Items.DIAMOND_BOOTS));
                    require(JustDireLavaRepairCompatibility.eligible(restored) && flag(restored, "lavarepair_toggle", false)
                            && !flag(restored, "tool_enabled", true)
                            && JustDireSettingsCompatibility.boundPassives(restored, 4, true).equals(List.of("lavarepair")), "Restoring eligibility reset root preferences");
                }));
            }
        }
        setup(client, true, false, 0); open(client); select(client, 36);
        assign(client, "STEPHEIGHT", 4, true); assign(client, "JUMPBOOST", 4, true);
        assign(client, "LAVAREPAIR", 4, true); assign(client, "NEGATEFALLDAMAGE", 4, true);
        close(client);
        add("shared mouse has native passive order and no source duplicates", () -> require(
                JustDireSettingsCompatibility.boundPassives(feet(client), 4, true).equals(List.of("stepheight", "jumpboost", "lavarepair", "negatefalldamage")), "Shared passive order wrong"));
        add("one shared input switches each supported passive off once without FE", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "lavarepair", "negatefalldamage").stream().noneMatch(name -> flag(feet(client), name + "_toggle", true)));
        add("one shared input re-enables all passives once", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "lavarepair", "negatefalldamage").stream().allMatch(name -> flag(feet(client), name + "_toggle", false)));
    }

    private static void lavaTooltip(Minecraft client, boolean enabled) {
        var lines = feet(client).getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        var intrinsic = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().equals("justdirethings.ability") && text.getArgs()[0] instanceof Component ability
                && ability.getContents() instanceof TranslatableContents name && name.getKey().equals("justdirethings.ability.lavarepair")).toList();
        require(intrinsic.size() == 1 && ((Component) ((TranslatableContents) intrinsic.getFirst().getContents()).getArgs()[1]).getContents()
                instanceof TranslatableContents state && state.getKey().equals("justdirethings." + (enabled ? "enabled" : "disabled")), "Root Lava tooltip missing/current state wrong");
    }

    private static void lavaDrop(ServerPlayer player, boolean enabled) {
        var level = player.level(); var source = new BlockPos(5, 80, 5);
        for (BlockPos pos : BlockPos.betweenClosed(3, 79, 3, 7, 87, 7)) {
            level.setBlock(pos, (pos.getY() == 79 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
        level.setBlock(source, Blocks.LAVA.defaultBlockState(), 3);
        var root = player.getItemBySlot(EquipmentSlot.FEET).copy(); root.setDamageValue(100);
        var beforeDrop = root.copy();
        var item = new ItemEntity(level, 5.5, 80.2, 5.5, root); item.setDeltaMovement(Vec3.ZERO);
        require(level.addFreshEntity(item), "Could not drop configured Lava root");
        for (int i = 0; i < 240; i++) { item.tick(); }
        require(item.isAlive() && level.getBlockState(source).is(enabled ? Blocks.OBSIDIAN : Blocks.LAVA), "Root controls did not govern drop/fire protection");
        if (enabled) { beforeDrop.setDamageValue(0); }
        require(ItemStack.matches(beforeDrop, item.getItem()), "Configured drop changed resources/history/controls or repair failed");
        item.discard(); JustDireLavaRepairCompatibility.clearEpisodes();
    }

    private static void poweredJump(Minecraft client) {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean passive : tier.equals("eclipsealloy") ? new boolean[]{false, true} : new boolean[]{false}) {
                for (boolean inverse : new boolean[]{false, true}) {
                    int maximum = tier.equals("celestigem") ? 3 : 5;
                    String label = tier + (passive ? "+Ferricore" : "+vanilla") + "/" + inverse;
                    var widget = new ExtendedSlider[1];
                    add("prepare powered Jump-only " + label + " at zero FE", () -> server(client, player -> {
                        var pair = MixedBootsFactory.createPair(NeoForgeJustDireJumpTest.poweredBoots(tier, 0), passive
                                ? NeoForgeJustDireJumpTest.boots("ferricore", true) : new ItemStack(Items.DIAMOND_BOOTS));
                        expected = inverse ? pair.inverse() : pair.primary();
                        provenance = ModContent.getMixedBootsData(expected).orElseThrow();
                        player.getInventory().clearContent(); player.setItemSlot(EquipmentSlot.FEET, expected);
                        player.inventoryMenu.broadcastChanges();
                    }), () -> client.screen == null && ItemStack.matches(expected, feet(client)));
                    open(client); select(client, 36);
                    add("native powered Jump slider sets permanent maximum " + label, () -> {
                        require(((Number) field(slider(client, "JUMPBOOST"), "maxValue")).intValue() == maximum, "Powered range missing");
                        slider(client, "JUMPBOOST").setValue(1); slider(client, "JUMPBOOST").setValue(maximum);
                    }, () -> value(feet(client), "jumpboost_value") == maximum);
                    add("global-off keeps powered Jump's native range " + label,
                            () -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)),
                            () -> !flag(feet(client), "tool_enabled", true));
                    reveal(client, "JUMPBOOST");
                    add("native powered Jump captures keyboard while depleted/off " + label, () -> {
                        require(((Number) field(slider(client, "JUMPBOOST"), "maxValue")).intValue() == maximum, "Global-off shrank slider");
                        click((Button) maps(client, "bindingButtons").get(icon(client, "JUMPBOOST")), 0);
                        client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0));
                    }, () -> JustDireSettingsCompatibility.boundPassives(feet(client), GLFW.GLFW_KEY_V, false).equals(List.of("jumpboost")));
                    close(client);
                    add("powered Jump keyboard toggles once without FE/global enablement " + label,
                            () -> press(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS),
                            () -> !flag(feet(client), "jumpboost_toggle", true) && charge(feet(client)) == 0
                                    && value(feet(client), "jumpboost_value") == maximum);
                    add("powered Jump keyboard restores preference without payment " + label,
                            () -> press(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS),
                            () -> flag(feet(client), "jumpboost_toggle", false) && charge(feet(client)) == 0);
                    open(client); select(client, 36); reveal(client, "JUMPBOOST");
                    add("native powered Jump replaces keyboard with mouse " + label, () -> {
                        click((Button) maps(client, "bindingButtons").get(icon(client, "JUMPBOOST")), 0);
                        client.screen.mouseClicked(mouse(1, 1, 3), false);
                    }, () -> JustDireSettingsCompatibility.boundPassives(feet(client), 3, true).equals(List.of("jumpboost")));
                    close(client);
                    add("powered Jump mouse toggles once without FE/global enablement " + label,
                            () -> mousePress(3, GLFW.GLFW_PRESS),
                            () -> !flag(feet(client), "jumpboost_toggle", true) && charge(feet(client)) == 0);
                    add("powered Jump mouse restores preference without payment " + label,
                            () -> mousePress(3, GLFW.GLFW_PRESS),
                            () -> flag(feet(client), "jumpboost_toggle", false) && charge(feet(client)) == 0);
                    open(client); select(client, 36);
                    add("native global shortcut restores powered Jump consumers " + label,
                            () -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)),
                            () -> flag(feet(client), "tool_enabled", false));
                    add("remember native powered slider identity " + label, () -> widget[0] = slider(client, "JUMPBOOST"));
                    for (int energy : new int[]{4, 5, 0}) {
                        add("ordinary charge packet reaches selected powered root " + label + "/" + energy, () -> server(client, player -> {
                            set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", energy);
                            player.inventoryMenu.broadcastChanges();
                        }), () -> charge(feet(client)) == energy && wait > 5);
                        add("powered range/tooltip/effect stay rooted across charge " + label + "/" + energy, () -> {
                            require(slider(client, "JUMPBOOST") == widget[0] && widget[0].getValueInt() == maximum
                                    && ((Number) field(widget[0], "maxValue")).intValue() == maximum, "Charge rebuilt/shrank/reclamped native slider");
                            assertPoweredJump(client, maximum, energy >= 5 ? maximum : passive ? 1 : 0);
                            require(bindingFor(feet(client), "jumpboost", 3, true)
                                    && ModContent.getMixedBootsData(feet(client)).orElseThrow().equals(provenance), "Charge erased binding or provenance");
                        });
                    }
                    close(client);
                }
            }
        }
    }

    private static void assertPoweredJump(Minecraft client, int configured, int usable) {
        var root = feet(client); var before = root.copy();
        var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        var upgrades = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().equals("tooltip.mismatchedboots.just_dire.upgrade_setting")
                && text.getArgs()[0] instanceof Component ability && ability.getContents() instanceof TranslatableContents name
                && name.getKey().equals("justdirethings.ability.jumpboost")).toList();
        require(upgrades.size() == 1 && ((TranslatableContents) upgrades.getFirst().getContents()).getArgs()[2].equals(configured),
                "Root tooltip lost/duplicated configured Jump value");
        var energy = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().equals("justdirethings.festored")).toList();
        require(energy.size() == 1 && ((TranslatableContents) energy.getFirst().getContents()).getArgs()[0]
                .equals(String.format(Locale.ROOT, "%,d", charge(root))), "Root tooltip retained historical FE");
        assertClientJump(client, usable);
        require(ItemStack.matches(before, root), "Presentation/Jump spent FE or changed root/source controls");
    }

    private static void assertClientJump(Minecraft client, int usable) {
        var root = feet(client); var before = root.copy();
        var player = client.player; var movement = player.getDeltaMovement();
        try {
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
            double ordinary = player.getDeltaMovement().y;
            player.setItemSlot(EquipmentSlot.FEET, root); player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
            require(Math.abs(player.getDeltaMovement().y - ordinary - usable / 7.5f) < 1e-7, "Edited root value did not reach actual client Jump");
        } finally { player.setItemSlot(EquipmentSlot.FEET, root); player.setDeltaMovement(movement); }
        require(ItemStack.matches(before, root), "Actual client Jump changed root state/resources");
    }

    private static void earthquake(Minecraft client) {
        add("observe genuine production Earthquake/GS replies", NeoForgeJustDireGroundStompClientTest::observeActiveReplies);
        earthquakeCases(client);
    }

    private static void observeActiveReplies() {
        JustDireActiveNetwork.clientReplies(reply -> {
            observedActivation = reply;
            if (holdReply && reply.prepared() && reply.accepted()) {
                held = new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), earthquakeKey, earthquakeMouse);
            } else { JustDireAbilityInput.reply(reply); }
            if (!reply.prepared() && reply.accepted()) {
                consumed = new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), earthquakeKey, earthquakeMouse);
            }
        }, JustDireSettingsClient::reply);
    }

    private static void earthquakeCases(Minecraft client) {
        for (boolean inverse : new boolean[]{false, true}) {
            earthquakeSetup(client, inverse, 0, 0);
            open(client); select(client, 36);
            add("native Earthquake toggle-only controls remain available at zero FE", () -> {
                require(icon(client, "EARTHQUAKE") != null && slider(client, "EARTHQUAKE") == null
                        && !JustDireSettingsCompatibility.metadata(feet(client), "earthquake").hasSlider(), "Dead Earthquake slider or missing zero-FE controls");
                before = feet(client).copy();
            });
            add("native Earthquake toggle off", () -> click(icon(client, "EARTHQUAKE"), 0), () -> !flag(feet(client), "earthquake_toggle", true));
            add("native Earthquake toggle on", () -> click(icon(client, "EARTHQUAKE"), 0), () -> flag(feet(client), "earthquake_toggle", false));
            reveal(client, "EARTHQUAKE");
            add("native Earthquake particles and equipped-only controls", () -> {
                var icon = icon(client, "EARTHQUAKE");
                require(!((Button) maps(client, "leftRightClickButtons").get(icon)).active
                        && !((Button) maps(client, "requireEquippedButtons").get(icon)).active, "Unsupported Earthquake binding choice");
                click((Button) maps(client, "customSettingsButtons").get(icon), 0);
            }, () -> value(feet(client), "earthquake_custom_setting") == 1);
            add("Earthquake capture rejects ordinary/out-of-range mouse buttons without consuming capture", () -> {
                before = feet(client).copy(); click((Button) maps(client, "bindingButtons").get(icon(client, "EARTHQUAKE")), 0);
                for (int button : new int[]{0, 1, 8}) { client.screen.mouseClicked(mouse(1, 1, button), false); }
                require((boolean) field(client.screen, "bindingEnabled"), "Invalid mouse consumed capture");
            }, () -> wait > 5 && ItemStack.matches(before, feet(client)));
            assign(client, "EARTHQUAKE", GLFW.GLFW_KEY_J, false);
            assign(client, "GROUNDSTOMP", GLFW.GLFW_KEY_G, false);
            add("Earthquake preferences preserve zero FE and immutable sources", () -> require(charge(feet(client)) == 0
                    && ModContent.getMixedBootsData(before).equals(ModContent.getMixedBootsData(feet(client))), "Settings spent FE or edited source history"));
            close(client); open(client); select(client, 36);
            add("reopened Earthquake controls retain live root preferences", () -> require(value(feet(client), "earthquake_custom_setting") == 1
                    && bindingFor(feet(client), "earthquake", GLFW.GLFW_KEY_J, false) && slider(client, "EARTHQUAKE") == null, "Reopen lost Earthquake preferences"));
            add("remove installation without granting a stale native control", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "earthquake_upgrade_installed", false); player.inventoryMenu.broadcastChanges();
            }), () -> !flag(feet(client), "earthquake_upgrade_installed", true));
            select(client, 36);
            add("native uninstalled Earthquake stays hidden", () -> require(!hasIcon(client, "EARTHQUAKE"), "Uninstalled ability advertised"));
            add("restore root installation for native capture", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "earthquake_upgrade_installed", true); player.inventoryMenu.broadcastChanges();
            }), () -> flag(feet(client), "earthquake_upgrade_installed", false));
            select(client, 36); reveal(client, "EARTHQUAKE");
            add("prepare an ineligible mixed inventory tool and start Earthquake capture", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "EARTHQUAKE")), 0);
                server(client, player -> {
                    var other = MixedBootsFactory.createPair(boot("celestigem", 0), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                    set(other, "earthquake_upgrade_installed", true); player.getInventory().setItem(9, other); player.inventoryMenu.broadcastChanges();
                });
            }, () -> !client.player.getInventory().getItem(9).isEmpty());
            select(client, 9);
            add("ordinary tool selection cancels Earthquake capture and excludes forged Celestigem", () -> require(
                    !(boolean) field(client.screen, "bindingEnabled") && !hasIcon(client, "EARTHQUAKE")
                            && bindingFor(feet(client), "earthquake", GLFW.GLFW_KEY_J, false), "Capture persisted or forged tier advertised"));
            select(client, 36);
            add("GUI input cannot execute Earthquake", () -> { observedActivation = null; press(GLFW.GLFW_KEY_J, GLFW.GLFW_PRESS); },
                    () -> wait > 6 && observedActivation == null && charge(feet(client)) == 0 && earthquakeClock(feet(client)) == null);
            close(client);
            add("recharge the actual equipped wallet before production input", () -> server(client, player -> {
                try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    require(JustDireResourceTestSupport.handler(player.getItemBySlot(EquipmentSlot.FEET)).insert(150_000, transaction) == 150_000, "Recharge failed");
                    transaction.commit();
                }
                player.inventoryMenu.broadcastChanges();
            }), () -> charge(feet(client)) == 150_000 && wait > 5);
            add("release/repeat/wrong-device input cannot activate Earthquake", () -> {
                observedActivation = null; press(GLFW.GLFW_KEY_J, GLFW.GLFW_RELEASE); press(GLFW.GLFW_KEY_J, GLFW.GLFW_REPEAT);
                mousePress(GLFW.GLFW_KEY_J, GLFW.GLFW_PRESS);
            }, () -> wait > 6 && observedActivation == null && charge(feet(client)) == 150_000 && earthquakeClock(feet(client)) == null);
            add("isolate the single paid target after native UI round trips", () -> server(client, player -> {
                // Chunk-loaded/naturally spawned mobs may arrive after earthquakeSetup's cleanup.
                // Keep the per-target bill assertion exact instead of accepting an incidental second bill.
                var extras = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8), mob -> mob != target);
                if (!extras.isEmpty()) {
                    games.alejandrocoria.mismatchedboots.Constants.LOG.info("Earthquake fixture removed incidental targets before one-target budget check: {}",
                            extras.stream().map(mob -> mob.getType() + "#" + mob.getId() + " at " + mob.position()).toList());
                }
                extras.forEach(Mob::discard);
                require(target.isAlive() && target.onGround(), "Expected grounded single target unavailable");
            }));
            add("native-bound Earthquake keyboard press pays once", () -> earthquakePress(GLFW.GLFW_KEY_J, false),
                    () -> charge(feet(client)) == 100_000 && earthquakeClock(feet(client)) != null
                            && observedActivation != null && !observedActivation.prepared() && observedActivation.accepted());
            add("the real grounded target received Slowness IV", () -> server(client, player -> require(target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) != null
                    && target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getAmplifier() == 3, "No authoritative Earthquake effect")));
            add("distinct Ground Stomp key works during Earthquake ticking", () -> earthquakePress(GLFW.GLFW_KEY_G, false),
                    () -> charge(feet(client)) == 95_000 && timer(feet(client)) > 0 && earthquakeClock(feet(client)) != null
                            && observedActivation != null && !observedActivation.prepared() && observedActivation.accepted());
            add("consumed production Ground Stomp replay cannot spend again", () -> Network.getNetworkHandler().sendToServer(consumed),
                    () -> observedActivation != null && !observedActivation.accepted() && charge(feet(client)) == 95_000);

            earthquakeSetup(client, inverse, 150_000, 200);
            open(client); select(client, 36); reveal(client, "EARTHQUAKE");
            add("begin Earthquake capture while actual timer packets replace the selected root", () -> {
                earthquakeWidget = (Button) maps(client, "bindingButtons").get(icon(client, "EARTHQUAKE"));
                before = feet(client).copy(); click(earthquakeWidget, 0);
            }, () -> wait > 6 && timer(feet(client)) < timer(before));
            add("ticking slot packets preserve native capture/widget identity and live selected reference", () -> {
                require((boolean) field(client.screen, "bindingEnabled") && maps(client, "bindingButtons").get(icon(client, "EARTHQUAKE")) == earthquakeWidget
                        && ((JustDireSettingsScreenAccess) client.screen).mismatchedboots$jdtTool() == feet(client), "Ordinary clock packets rebuilt capture or retained a stale root");
                client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_J, 0, 0));
            }, () -> bindingFor(feet(client), "earthquake", GLFW.GLFW_KEY_J, false));
            close(client);
            add("hold a genuine paid-only Earthquake reply while Ground Stomp decrements", () -> {
                holdReply = true; held = null; before = feet(client).copy(); inputRoot = feet(client); earthquakePress(GLFW.GLFW_KEY_J, false);
            }, () -> held != null && wait > 10 && feet(client) != inputRoot);
            add("release the actual Earthquake reply through production paid validation", () -> {
                require(fieldStatic(JustDireAbilityInput.class, "pending") != null, "Peer-clock packets cancelled paid EQ intent");
                holdReply = false; JustDireAbilityInput.reply(observedActivation);
            }, () -> charge(feet(client)) == 100_000 && earthquakeClock(feet(client)) != null
                    && observedActivation != null && !observedActivation.prepared() && observedActivation.accepted());
        }
        for (String change : List.of("charge", "gui", "replacement")) {
            earthquakeSetup(client, false, 150_000, 200);
            add("hold a real EQ permit before " + change, () -> {
                holdReply = true; held = null; earthquakePress(GLFW.GLFW_KEY_J, false);
            }, () -> held != null);
            if (change.equals("gui")) { open(client); }
            else {
                add("apply actual equipped " + change + " while the paid reply is held", () -> server(client, player -> {
                    var root = player.getItemBySlot(EquipmentSlot.FEET);
                    if (change.equals("replacement")) { player.setItemSlot(EquipmentSlot.FEET, root.copy()); }
                    else {
                        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                            require(JustDireResourceTestSupport.handler(root).insert(1_000, transaction) == 1_000, "Stale flight recharge failed"); transaction.commit();
                        }
                    }
                    player.inventoryMenu.broadcastChanges();
                }), () -> wait > 5 && charge(feet(client)) == (change.equals("charge") ? 151_000 : 150_000));
            }
            add("release a genuine reply after " + change + " without authorizing a stale paid action", () -> {
                var reply = observedActivation; observedActivation = null; holdReply = false; JustDireAbilityInput.reply(reply);
            }, () -> wait > 6 && earthquakeClock(feet(client)) == null
                    && charge(feet(client)) == (change.equals("charge") ? 151_000 : 150_000)
                    && (change.equals("replacement") ? observedActivation != null && !observedActivation.prepared() && !observedActivation.accepted()
                    : observedActivation == null && fieldStatic(JustDireAbilityInput.class, "pending") == null));
            if (change.equals("gui")) { close(client); }
        }
        for (int button : new int[]{GLFW.GLFW_KEY_K, 2, 7}) {
            boolean mouse = button != GLFW.GLFW_KEY_K;
            earthquakeSetup(client, false, 55_000, 0);
            open(client); select(client, 36);
            assign(client, "EARTHQUAKE", button, mouse); assign(client, "GROUNDSTOMP", button, mouse);
            add("install Step's shared free switch on the live root", () -> server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "stepheight_upgrade_installed", true); player.inventoryMenu.broadcastChanges();
            }), () -> flag(feet(client), "stepheight_upgrade_installed", false));
            select(client, 36); assign(client, "STEPHEIGHT", button, mouse);
            close(client);
            add("shared native " + (mouse ? "mouse" : "key") + " runs GS then Earthquake then the passive switch once", () -> earthquakePress(button, mouse),
                    () -> charge(feet(client)) == 0 && timer(feet(client)) > 0 && earthquakeClock(feet(client)) != null
                            && !flag(feet(client), "stepheight_toggle", true));
            add("depleted shared binding can still re-enable the passive without another bill", () -> earthquakePress(button, mouse),
                    () -> flag(feet(client), "stepheight_toggle", false) && charge(feet(client)) == 0);
            open(client); select(client, 36); assign(client, "EARTHQUAKE", GLFW.GLFW_KEY_ESCAPE, false);
            add("Earthquake Escape removal leaves the shared GS/passive bindings intact", () -> require(noBinding(feet(client), "earthquake")
                    && bindingFor(feet(client), "groundstomp", button, mouse) && bindingFor(feet(client), "stepheight", button, mouse), "Removal lost other bindings"));
            close(client);
        }
        earthquakeSetup(client, false, 150_000, 0);
        add("hold a genuine paid Earthquake permit before disconnect", () -> {
            holdReply = true; held = null; earthquakePress(GLFW.GLFW_KEY_J, false);
        }, () -> held != null);
        add("remember paid-flight resources in the current registry", () -> snapshotReconnect(client));
        add("disconnect with the genuine paid Earthquake intent pending", client::disconnectWithSavingScreen,
                () -> client.player == null && client.getSingleplayerServer() == null);
        add("production disconnect clears paid Earthquake intent", () -> require(
                fieldStatic(JustDireAbilityInput.class, "pending") == null
                        && (int) fieldStatic(JustDireAbilityInput.class, "sequence") == 0, "Disconnect retained paid intent"));
        add("rejoin the same world after the paid-flight disconnect", () -> client.createWorldOpenFlows().openWorld("jump", () -> client.setScreen(new TitleScreen())),
                () -> joined(client) && client.screen == null);
        add("decode the unpaid root in the new connection registry", () -> { restoreReconnect(client); holdReply = false; });
        add("ordinary login packets retain resources without executing the held Earthquake", () -> {},
                () -> sameSettings(expected, feet(client)) && charge(feet(client)) == 150_000 && earthquakeClock(feet(client)) == null);
        add("an old unconsumed paid nonce cannot execute after reconnect", () -> {
            observedActivation = null; Network.getNetworkHandler().sendToServer(held);
        }, () -> observedActivation != null && !observedActivation.accepted()
                && charge(feet(client)) == 150_000 && earthquakeClock(feet(client)) == null);
    }

    private static void earthquakeSetup(Minecraft client, boolean inverse, int energy, int stompTicks) {
        add("fresh production Earthquake root " + inverse + "/" + energy + "/" + stompTicks, () -> server(client, player -> {
            try {
                JustDireEarthquakeTestSupport.bind();
                player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8)).forEach(Mob::discard);
                expected = JustDireEarthquakeTestSupport.mixed(energy, inverse, false);
                set(expected, "groundstomp_upgrade_installed", true); set(expected, "groundstomp_bindingmode", 2);
                set(expected, "ability_bindings", List.of(JustDireEarthquakeTestSupport.binding("earthquake", GLFW.GLFW_KEY_J),
                        JustDireEarthquakeTestSupport.binding("groundstomp", GLFW.GLFW_KEY_G)));
                if (stompTicks > 0) { expected.set(JustDireEarthquakeTestSupport.timers(), List.of(JustDireEarthquakeTestSupport.timer("groundstomp", stompTicks, false))); }
                player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
                target = EntityType.COW.create(player.level(), EntitySpawnReason.COMMAND); require(target != null, "No real EQ target");
                target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true); target.setPos(player.getX() + 1, player.getY(), player.getZ());
                require(player.level().addFreshEntity(target), "Target not inserted"); target.setOnGround(true);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }), () -> client.screen == null && charge(feet(client)) == energy && wait > 5);
    }

    private static void earthquakePress(int key, boolean mouse) {
        earthquakeKey = key; earthquakeMouse = mouse; observedActivation = null;
        if (mouse) { mousePress(key, GLFW.GLFW_PRESS); } else { press(key, GLFW.GLFW_PRESS); }
    }

    private static JustDireEarthquakeTestSupport.Clock earthquakeClock(ItemStack root) {
        try { return JustDireEarthquakeTestSupport.clock(root, "earthquake"); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static boolean hasIcon(Minecraft client, String name) {
        return maps(client, "buttonToAbilityMap").values().stream().anyMatch(value -> value.toString().equals(name));
    }

    private static void earthquakeUnavailable(Minecraft client) {
        setup(client, true, false, 0); open(client); select(client, 36);
        add("EQ-only failure hides its native widgets without poisoning older settings", () -> require(
                !hasIcon(client, "EARTHQUAKE") && hasIcon(client, "GROUNDSTOMP") && JustDireSettingsCompatibility.available()
                        && JustDireSettingsCompatibility.metadata(feet(client), "earthquake") == null, "EQ failure disabled old metadata or advertised partial support"));
        add("native Ground Stomp editing survives EQ failure", () -> slider(client, "GROUNDSTOMP").setValue(2),
                () -> value(feet(client), "groundstomp_value") == 2);
        close(client);
        add("shared imported bindings cannot smuggle unavailable EQ through GS", () -> server(client, player -> {
            try {
                JustDireEarthquakeTestSupport.bind(); var root = player.getItemBySlot(EquipmentSlot.FEET);
                set(root, "forge_energy", 55_000); set(root, "earthquake_bindingmode", 2);
                set(root, "ability_bindings", List.of(JustDireEarthquakeTestSupport.binding("groundstomp", GLFW.GLFW_KEY_G),
                        JustDireEarthquakeTestSupport.binding("earthquake", GLFW.GLFW_KEY_G)));
                if (VARIANT.equals("earthquakeSettingsFailure")) {
                    require(JustDireEarthquakeCompatibility.available() && JustDireEarthquakeCompatibility.quote(root) != null, "UI failure poisoned server contract");
                    root.set(JustDireEarthquakeTestSupport.timers(), List.of(JustDireEarthquakeTestSupport.timer("earthquake", 1, true)));
                }
                player.inventoryMenu.broadcastChanges();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }), () -> charge(feet(client)) == 55_000 && wait > 5);
        add("old equipped GS still executes on the shared key", () -> press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS),
                () -> charge(feet(client)) == 50_000 && timer(feet(client)) > 0);
        if (VARIANT.equals("earthquakeSettingsFailure")) {
            add("unsupported GUI metadata does not freeze an already spent EQ clock", () -> require(
                    earthquakeClock(feet(client)) != null && !earthquakeClock(feet(client)).active() && earthquakeClock(feet(client)).ticks() < 800,
                    "UI failure froze/reset the owned clock"));
        } else { add("missing EQ action remains unavailable", () -> require(!JustDireEarthquakeCompatibility.available(), "Failure was not injected")); }
    }

    private static void passiveBindings(Minecraft client) {
        var names = List.of("STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE");
        open(client); select(client, 36);
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index), path = name.toLowerCase(java.util.Locale.ROOT);
            int key = GLFW.GLFW_KEY_J + index;
            reveal(client, name);
            add("mixed native " + name + " captures its keyboard binding", () -> {
                require(!((Button) maps(client, "requireEquippedButtons").get(icon(client, name))).active, "Passive inventory activation advertised");
                click((Button) maps(client, "bindingButtons").get(icon(client, name)), 0);
                client.screen.keyPressed(new KeyEvent(key, 0, 0));
            }, () -> JustDireSettingsCompatibility.boundPassives(feet(client), key, false).equals(List.of(path)));
        }
        close(client);
        add("prepare disabled depleted root without changing native bindings", () -> server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET); set(root, "tool_enabled", false); set(root, "forge_energy", 0);
            player.inventoryMenu.broadcastChanges(); expected = root.copy();
        }), () -> ItemStack.matches(expected, feet(client)));
        for (int index = 0; index < names.size(); index++) {
            String path = names.get(index).toLowerCase(java.util.Locale.ROOT);
            int key = GLFW.GLFW_KEY_J + index;
            add("passive " + path + " ignores release/repeat/wrong device", () -> {
                before = feet(client).copy(); press(key, GLFW.GLFW_RELEASE); press(key, GLFW.GLFW_REPEAT); mousePress(key, GLFW.GLFW_PRESS);
            }, () -> wait > 8 && ItemStack.matches(before, feet(client)));
            add("equipped " + path + " keyboard toggles off at zero FE/global off", () -> {
                before = feet(client).copy(); press(key, GLFW.GLFW_PRESS);
            }, () -> !flag(feet(client), path + "_toggle", true) && charge(feet(client)) == 0
                    && value(feet(client), "jumpboost_value") == 2 && ModContent.getMixedBootsData(feet(client)).orElseThrow().equals(provenance));
            add("equipped " + path + " keyboard toggles back on without action eligibility", () -> press(key, GLFW.GLFW_PRESS),
                    () -> flag(feet(client), path + "_toggle", false) && !flag(feet(client), "tool_enabled", true) && charge(feet(client)) == 0);
        }
        add("restore live consumers without resetting bindings", () -> server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET); set(root, "tool_enabled", true); set(root, "forge_energy", 6_000);
            player.inventoryMenu.broadcastChanges();
        }), () -> flag(feet(client), "tool_enabled", false) && charge(feet(client)) == 6_000
                && client.player.getAttributeValue(Attributes.STEP_HEIGHT) > client.player.getAttributeBaseValue(Attributes.STEP_HEIGHT));
        add("Step input immediately lowers equipped client attribute", () -> {
            enabledStep = client.player.getAttributeValue(Attributes.STEP_HEIGHT); press(GLFW.GLFW_KEY_J, GLFW.GLFW_PRESS);
        }, () -> !flag(feet(client), "stepheight_toggle", true) && client.player.getAttributeValue(Attributes.STEP_HEIGHT) < enabledStep);
        add("Step input restores equipped client attribute without payment", () -> press(GLFW.GLFW_KEY_J, GLFW.GLFW_PRESS),
                () -> flag(feet(client), "stepheight_toggle", false) && client.player.getAttributeValue(Attributes.STEP_HEIGHT) == enabledStep && charge(feet(client)) == 6_000);
        open(client); select(client, 36);
        for (String name : names) {
            String path = name.toLowerCase(java.util.Locale.ROOT);
            reveal(client, name);
            add("mixed native " + name + " replaces keyboard with shared mouse binding", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, name)), 0);
                client.screen.mouseClicked(mouse(1, 1, 4), false);
            }, () -> JustDireSettingsCompatibility.boundPassives(feet(client), 4, true).contains(path));
        }
        add("GUI blocks shared passive gameplay input", () -> { before = feet(client).copy(); mousePress(4, GLFW.GLFW_PRESS); },
                () -> wait > 8 && ItemStack.matches(before, feet(client)));
        close(client); open(client); select(client, 36);
        add("native reopen retains all passive mouse modes/preferences", () -> require(
                JustDireSettingsCompatibility.boundPassives(feet(client), 4, true).equals(List.of("stepheight", "jumpboost", "negatefalldamage")), "Passive reopen lost preferences"));
        close(client);
        add("shared mouse switches all supported passives off without payment", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> !flag(feet(client), name + "_toggle", true)) && charge(feet(client)) == 6_000);
        add("shared mouse switches all supported passives back on", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> flag(feet(client), name + "_toggle", false)) && charge(feet(client)) == 6_000);
        open(client); select(client, 36);
        reveal(client, "GROUNDSTOMP");
        add("native Ground Stomp capture shares the passive mouse button", () -> {
            click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
            client.screen.mouseClicked(mouse(1, 1, 4), false);
        }, () -> JustDireSettingsCompatibility.groundStompBinding(feet(client), 4, true));
        close(client);
        add("shared native mouse input performs one Ground Stomp and all passive switches", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> charge(feet(client)) == 1_000 && timer(feet(client)) > 0
                        && List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> !flag(feet(client), name + "_toggle", true)));
        add("shared input during active cooldown still re-enables all passives without another bill", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> charge(feet(client)) == 1_000 && timer(feet(client)) > 0
                        && List.of("stepheight", "jumpboost", "negatefalldamage").stream().allMatch(name -> flag(feet(client), name + "_toggle", false)));
        open(client); select(client, 36);
        for (String name : List.of("STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE", "GROUNDSTOMP")) {
            String path = name.toLowerCase(java.util.Locale.ROOT);
            reveal(client, name);
            add("native Escape removes " + name + " without removing other records", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, name)), 0);
                client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            }, () -> path.equals("groundstomp") ? !JustDireSettingsCompatibility.groundStompBinding(feet(client), 4, true)
                    : !JustDireSettingsCompatibility.boundPassives(feet(client), 4, true).contains(path));
        }
        close(client);
        add("removed bindings denial starts with charged root and expired active cooldown", () -> server(client, player -> {
            set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
        }), () -> charge(feet(client)) == 6_000 && timer(feet(client)) == -1);
        add("removed passive bindings do not toggle", () -> { before = feet(client).copy(); mousePress(4, GLFW.GLFW_PRESS); },
                () -> wait > 8 && ItemStack.matches(before, feet(client)));
    }

    private static void unavailable(Minecraft client) {
        setup(client, true, false, 6_000); open(client);
        add("incomplete UI leaves mixed selection unavailable but empty native menu intact", () -> {
            require(!((JustDireSettingsScreenAccess) client.screen).mismatchedboots$jdtReady(), "Incomplete route published readiness");
            require(((ItemStack) field(client.screen, "tool")).isEmpty(), "Incomplete route selected mixed");
            require(maps(client, "buttonToAbilityMap").isEmpty(), "Incomplete UI advertised controls");
            assertInternalSlots(((AbstractContainerScreen<?>) client.screen).getMenu());
        });
        add("mixed callbacks never fall through to native edits", () -> {
            var screen = (JustDireSettingsScreenAccess) client.screen;
            screen.mismatchedboots$jdtTool(feet(client)); before = feet(client).copy();
            require(JustDireSettingsClient.edit(screen, "groundstomp", 1, 0, -1, false, true), "Mixed callback fell through");
            try { client.screen.getClass().getMethod("refreshButtons").invoke(client.screen); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            require(maps(client, "buttonToAbilityMap").isEmpty(), "Incomplete refresh advertised controls");
        }, () -> wait > 12 && ItemStack.matches(before, feet(client)));
        close(client);
        add("independent storage and existing consumers remain available", () -> {
            require(JustDireResourceTestSupport.handler(feet(client)) != null, "Storage disabled by UI failure");
            require(games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility.available(), "Ground Stomp API disabled by UI failure");
            assertClientJump(client, 5);
        });
    }

    private static boolean joined(Minecraft client) {
        return client.player != null && client.level != null && CompositionRulesClientLifecycle.areRulesSynchronized();
    }

    private static void observeReplies() {
        // Observe/defer a genuine production permit, not a second transport/action implementation.
        JustDireActiveNetwork.clientReplies(reply -> {
            observedActivation = reply;
            if (holdReply && reply.prepared() && reply.accepted()) {
                held = new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), 4, true);
            } else { JustDireAbilityInput.reply(reply); }
            if (!reply.prepared() && reply.accepted()) {
                consumed = new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), GLFW.GLFW_KEY_G, false);
            }
        }, JustDireSettingsClient::reply);
    }

    private static void lifecycle(Minecraft client) {
        require(LIFECYCLE.equals("save") || LIFECYCLE.equals("restart"), "Unknown lifecycle mode");
        add("observe completion events without replacement ticking or emission", () -> server(client, JustDireCooldownFeedbackTestSupport::begin));
        add("observe production replies without replacing native callbacks or input", NeoForgeJustDireGroundStompClientTest::observeReplies);
        if (LIFECYCLE.equals("restart")) {
            add("new JVM loads the exact disk stacks and paused timers", () -> server(client, player -> {
                try { JustDireGroundStompTestSupport.initializeApi(); JustDireGroundStompLifecycleTestSupport.verifyDisk(player); }
                catch (Exception failure) { throw new AssertionError(failure); }
                JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
            }));
        }
        for (int orientation = 0; orientation < 2; orientation++) {
            int slot = orientation;
            if (LIFECYCLE.equals("save")) { lifecycleSave(client, slot); }
            else { lifecycleRestart(client, slot); }
        }
        add("observe genuine joint active replies for Earthquake lifecycle", NeoForgeJustDireGroundStompClientTest::observeActiveReplies);
        for (int phase = 0; phase < 2; phase++) {
            if (LIFECYCLE.equals("save")) { earthquakeSave(client, phase); }
            else { earthquakeRestart(client, phase); }
        }
        if (LIFECYCLE.equals("save")) {
            add("stored roots retain exact timers through ordinary unequipped ticks", () -> {}, () -> wait > 20);
            add("verify all four stored joint roots before the real world save", () -> server(client, player -> {
                try { JustDireGroundStompLifecycleTestSupport.verifyDisk(player); }
                catch (Exception failure) { throw new AssertionError(failure); }
                JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
            }));
        } else {
            add("restore the existing passive reconnect observer", NeoForgeJustDireGroundStompClientTest::observeReplies);
            reconnect(client);
            add("reconnected paid Earthquake finishes through ordinary worn progression", () -> {},
                    () -> earthquakeClock(feet(client)) == null && timer(feet(client)) == -1, 1_200);
            add("restart/tier/reconnect produced no load-only or repeated completion cue", () -> {
                // The last root began with two saved inactive lockouts, then paid for one fresh EQ.
                JustDireCooldownFeedbackTestSupport.assertCompletions(3, 1);
                JustDireCooldownFeedbackTestSupport.assertHud(client, 0);
            });
        }
        add("release world feedback observer after lifecycle acceptance", () -> server(client, player -> {
            JustDireCooldownFeedbackTestSupport.capturingSounds = false;
            JustDireCooldownFeedbackTestSupport.observedPlayer = null;
        }));
    }

    private static void earthquakeSave(Minecraft client, int phase) {
        add("create production Eclipse/Celestigem lifecycle orientation " + phase, () -> server(client, player -> {
            try { JustDireEarthquakeTestSupport.bind(); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
            JustDireCooldownFeedbackTestSupport.begin(player);
            var eclipse = boot("eclipsealloy", 0); var celestigem = boot("celestigem", 0);
            for (var source : List.of(eclipse, celestigem)) {
                set(source, "jumpboost_value", 1); set(source, "jumpboost_toggle", false); set(source, "jumpboost_bindingmode", 2);
            }
            var pair = MixedBootsFactory.createPair(eclipse, celestigem);
            expected = phase == 0 ? pair.primary() : pair.inverse(); expected.setDamageValue(31);
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                require(JustDireResourceTestSupport.handler(expected).insert(150_000, transaction) == 150_000, "Lifecycle funding failed"); transaction.commit();
            }
            set(expected, "earthquake_value", 123); // Retained, unused native scalar must not appear as a tooltip setting.
            player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
            provenance = ModContent.getMixedBootsData(expected).orElseThrow();
        }), () -> client.screen == null && charge(feet(client)) == 150_000 && wait > 5);
        open(client); select(client, 36);
        add("native widgets configure live GS strength and Earthquake particles", () -> {
            slider(client, "GROUNDSTOMP").setValue(2); slider(client, "GROUNDSTOMP").setValue(1);
        }, () -> value(feet(client), "groundstomp_value") == 1);
        reveal(client, "EARTHQUAKE");
        add("native Earthquake particle switch persists on root", () -> click((Button) maps(client, "customSettingsButtons").get(icon(client, "EARTHQUAKE")), 0),
                () -> value(feet(client), "earthquake_custom_setting") == 1);
        assign(client, "EARTHQUAKE", phase == 0 ? GLFW.GLFW_KEY_J : 3, phase != 0);
        assign(client, "GROUNDSTOMP", GLFW.GLFW_KEY_G, false); assign(client, "STEPHEIGHT", 4, true);
        close(client);
        earthquakeTarget(client);
        add("genuine configured Earthquake pays once before phase " + phase + " save", () -> {
            consumed = null; earthquakePress(phase == 0 ? GLFW.GLFW_KEY_J : 3, phase != 0);
        }, () -> consumed != null && charge(feet(client)) == 100_000 && earthquakeClock(feet(client)) != null);
        add("capture the paid EQ nonce and authoritative effect without changing sources", () -> {
            savedEarthquakePermit = consumed;
            server(client, player -> require(target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) != null
                    && target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getAmplifier() == 3
                    && ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "EQ effect/history missing"));
        });
        if (phase == 1) {
            add("actual worn ticker reaches inactive Earthquake before saving", () -> {},
                    () -> earthquakeClock(feet(client)) != null && !earthquakeClock(feet(client)).active());
        }
        add("GS pays once beside the independent EQ clock before storage", () -> earthquakePress(GLFW.GLFW_KEY_G, false),
                () -> charge(feet(client)) == 95_000 && timer(feet(client)) > 0 && consumed != null
                        && observedActivation != null && !observedActivation.prepared() && observedActivation.accepted());
        add("hold a real unconsumed passive permit before the JVM exits", () -> {
            holdReply = true; held = null; earthquakePress(4, true);
        }, () -> held != null);
        add("packet-delivered joint spent phase has exact native HUD before save", () -> JustDireCooldownFeedbackTestSupport.assertHud(client, 2));
        add("store spent root with its native phase, removed installation/disabled controls and both clocks", () -> server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET); var clock = earthquakeClock(root);
            require(clock != null && clock.active() == (phase == 0) && clock.ticks() > 0
                    && timer(root) > 0 && charge(root) == 95_000, "Wrong phase/resource state at save");
            if (phase == 0) {
                set(root, "earthquake_upgrade_installed", false); set(root, "tool_enabled", false);
                // This fixture simulates removal, not a supported uninstall. A later paid
                // reinstall must not retain an entitlement for the purchase it removed.
                var credits = root.get(JustDireRefundCompatibility.component());
                require(credits != null && credits.names().contains("earthquake"), "Missing original EQ purchase control");
                root.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(credits.names().stream()
                        .filter(name -> !name.equals("earthquake")).toList()));
            }
            else { set(root, "earthquake_toggle", false); }
            // Both sources advertise Jump, but only the live root may retain installation/preferences.
            set(root, "jumpboost_upgrade_installed", phase != 0);
            for (String name : List.of("jumpboost_value", "jumpboost_toggle", "jumpboost_bindingmode")) {
                root.remove(JustDireAttributeTestSupport.component(name));
            }
            target.discard(); target = null;
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            JustDireGroundStompLifecycleTestSupport.save(player, phase + 2, root, savedEarthquakePermit, held);
            // Saving/storage itself must not invent a completion, even after a genuine active-end in phase 1.
            JustDireCooldownFeedbackTestSupport.begin(player);
            player.inventoryMenu.broadcastChanges();
        }), () -> feet(client).isEmpty());
        add("release the observer without forwarding the old intent", () -> holdReply = false);
    }

    private static void earthquakeRestart(Minecraft client, int phase) {
        add("tier smithing and root reinstallation preserve spent Earthquake phase " + phase, () -> server(client, player -> {
            try {
                player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                JustDireCooldownFeedbackTestSupport.begin(player);
                var root = JustDireGroundStompLifecycleTestSupport.storage(player, false).removeItemNoUpdate(phase + 2);
                var clock = earthquakeClock(root);
                require(clock != null && clock.active() == (phase == 0) && charge(root) == 95_000 && timer(root) > 0, "Saved EQ phase/charge lost");
                expected = JustDireGroundStompLifecycleTestSupport.smithing(player, root, phase == 1);
                if (phase == 0) {
                    require(!flag(expected, "earthquake_upgrade_installed", true) && !flag(expected, "tool_enabled", true), "Tier restored historical installation/controls");
                    expected = JustDireGroundStompLifecycleTestSupport.installEarthquake(player, expected);
                } else { require(!flag(expected, "earthquake_toggle", true), "Tier restored historical EQ toggle"); }
                JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
                player.getInventory().clearContent(); provenance = ModContent.getMixedBootsData(expected).orElseThrow();
                player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
            } catch (Exception failure) { throw new AssertionError(failure); }
        }), () -> charge(feet(client)) == 95_000 && ModContent.getMixedBootsData(feet(client)).equals(java.util.Optional.ofNullable(provenance)) && wait > 5);
        feedbackPause(client, 2);
        add("disk/tier preserves absent Jump preferences and explicit installation on two powered halves", () -> {
            var root = feet(client);
            require(flag(root, "jumpboost_upgrade_installed", true) == (phase != 0) && noBinding(root, "jumpboost"), "Tier restored historical Jump installation/binding");
            for (String name : List.of("jumpboost_value", "jumpboost_toggle", "jumpboost_bindingmode")) {
                require(!root.has(JustDireAttributeTestSupport.component(name)), "Tier materialized absent Jump control: " + name);
            }
            assertClientJump(client, phase == 0 ? 0 : 5);
            serverJump(client, phase == 0 ? 0 : 5);
        });
        replaySaved(client, phase + 2);
        open(client); select(client, 36);
        add("native tier inventory shows EQ without a slider or an unused tooltip number", () -> {
            require(hasIcon(client, "EARTHQUAKE") && slider(client, "EARTHQUAKE") == null
                    && value(feet(client), "earthquake_custom_setting") == 1
                    && bindingFor(feet(client), "earthquake", phase == 0 ? GLFW.GLFW_KEY_J : 3, phase != 0), "Restart/tier UI reset EQ preferences");
            assertEarthquakeTooltip(client, 95_000);
        });
        add("native root controls re-enable the saved pair without resetting its spent clock", () -> {
            if (phase == 0) { client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0)); }
            else { click(icon(client, "EARTHQUAKE"), 0); }
        }, () -> flag(feet(client), "tool_enabled", true) && flag(feet(client), "earthquake_toggle", true));
        close(client);
        add("restored whole-tool control cannot resurrect removed Jump or historical disabled preferences", () -> {
            assertClientJump(client, phase == 0 ? 0 : 5); serverJump(client, phase == 0 ? 0 : 5);
        });
        earthquakeTarget(client);
        add("persisted Earthquake blocks the genuine configured input during lockout", () -> earthquakePress(phase == 0 ? GLFW.GLFW_KEY_J : 3, phase != 0),
                () -> observedActivation != null && observedActivation.prepared() && !observedActivation.accepted() && charge(feet(client)) == 95_000);
        if (phase == 0) {
            add("loaded active phase advances to native inactive cooldown without replay", () -> {},
                    () -> earthquakeClock(feet(client)) != null && !earthquakeClock(feet(client)).active());
            add("loaded active-end emitted once and inactive phase still uses native draws", () -> {
                require(JustDireCooldownFeedbackTestSupport.sounds.stream().filter(sound -> sound.event() == net.minecraft.sounds.SoundEvents.CONDUIT_DEACTIVATE).count() == 1,
                        "Loaded active-end was missing or duplicated");
                JustDireCooldownFeedbackTestSupport.assertHud(client, timer(feet(client)) > 0 ? 2 : 1);
            });
        }
        add("loaded remaining EQ lockout expires once through the actual worn ticker", () -> {},
                () -> earthquakeClock(feet(client)) == null && timer(feet(client)) == -1, 1_200);
        add("expiry retained FE/provenance and never applied the effect to the later target", () -> server(client, player -> require(
                charge(player.getItemBySlot(EquipmentSlot.FEET)) == 95_000 && target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) == null
                        && ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "Restart replayed/refilled EQ")));
        add("loaded joint clocks emit exactly once per committed boundary and remove their HUD", () -> {
            JustDireCooldownFeedbackTestSupport.assertCompletions(2, phase == 0 ? 1 : 0);
            JustDireCooldownFeedbackTestSupport.assertHud(client, 0);
        });
        replaySaved(client, phase + 2);
        add("fresh persisted binding on two Eclipse halves executes only one EQ action", () -> {
            consumed = null; earthquakePress(phase == 0 ? GLFW.GLFW_KEY_J : 3, phase != 0);
        }, () -> consumed != null && charge(feet(client)) == 45_000 && earthquakeClock(feet(client)) != null);
        add("actual fresh effect, live-energy tooltip and immutable upgraded sources agree", () -> {
            assertEarthquakeTooltip(client, 45_000);
            server(client, player -> require(target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) != null
                    && ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "Fresh EQ effect/history missing"));
        });
    }

    private static void earthquakeTarget(Minecraft client) {
        add("spawn one real grounded late EQ target in the disposable world", () -> server(client, player -> {
            player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8)).forEach(Mob::discard);
            target = EntityType.COW.create(player.level(), EntitySpawnReason.COMMAND); require(target != null, "No EQ target");
            target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true); target.setPos(player.getX() + 1, player.getY(), player.getZ());
            require(player.level().addFreshEntity(target), "EQ target not added"); target.setOnGround(true);
        }));
    }

    private static void assertEarthquakeTooltip(Minecraft client, int charge) {
        var root = feet(client); var before = root.copy();
        var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        var energy = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text && text.getKey().equals("justdirethings.festored")).toList();
        int nativeCapacity = JustDireResourceTestSupport.handler(boot("eclipsealloy", 0)).getCapacityAsInt();
        require(energy.size() == 1 && JustDireResourceTestSupport.handler(root).getCapacityAsInt() == nativeCapacity, "Duplicate/historical FE or doubled capacity");
        var args = ((TranslatableContents) energy.getFirst().getContents()).getArgs();
        require(args[0].equals(String.format(Locale.ROOT, "%,d", charge)) && args[1].equals(String.format(Locale.ROOT, "%,d", nativeCapacity)), "Stale root FE tooltip");
        var earthquake = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().startsWith("tooltip.mismatchedboots.just_dire.upgrade") && text.getArgs().length > 0
                && text.getArgs()[0] instanceof Component ability && ability.getContents() instanceof TranslatableContents name
                && name.getKey().equals("justdirethings.ability.earthquake")).toList();
        require(earthquake.size() == 1 && ((TranslatableContents) earthquake.getFirst().getContents()).getArgs().length == 2
                && ItemStack.matches(root, before), "EQ inventory duplicated/mutating or advertised unused numeric value");
    }

    private static void lifecycleSave(Minecraft client, int slot) {
        add("create lifecycle orientation " + slot + " through production", () -> server(client, player -> {
            try { JustDireGroundStompTestSupport.initializeApi(); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            client.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
            JustDireCooldownFeedbackTestSupport.begin(player);
            var powered = boot("celestigem", 10_000); var passive = boot("blazegold", 0);
            set(powered, "jumpboost_value", 1); set(passive, "jumpboost_value", 1);
            var pair = MixedBootsFactory.createPair(powered, passive);
            expected = slot == 0 ? pair.primary() : pair.inverse();
            lifecycleRefundCredits = expected.get(JustDireRefundCompatibility.component());
            require(lifecycleRefundCredits != null && !lifecycleRefundCredits.names().isEmpty(), "Native UI lifecycle lacks purchased credits");
            expected.setDamageValue(31); set(expected, "forge_energy", 10_000);
            provenance = ModContent.getMixedBootsData(expected).orElseThrow();
            player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
        }), () -> client.screen == null && ItemStack.matches(expected, feet(client)));
        open(client); select(client, 36);
        add("native widgets set persistent strength and powered Jump value distinct from history", () -> {
            slider(client, "GROUNDSTOMP").setValue(1); slider(client, "GROUNDSTOMP").setValue(3);
            slider(client, "JUMPBOOST").setValue(1); slider(client, "JUMPBOOST").setValue(3);
            click(icon(client, "STEPHEIGHT"), 0);
        }, () -> value(feet(client), "groundstomp_value") == 3 && value(feet(client), "jumpboost_value") == 3
                && !flag(feet(client), "stepheight_toggle", true));
        reveal(client, "GROUNDSTOMP");
        add("native particle preference is live root state", () -> click((Button) maps(client, "customSettingsButtons").get(icon(client, "GROUNDSTOMP")), 0),
                () -> value(feet(client), "groundstomp_custom_setting") == 1);
        assign(client, "GROUNDSTOMP", GLFW.GLFW_KEY_G, false);
        assign(client, "STEPHEIGHT", 4, true); assign(client, "JUMPBOOST", 4, true);
        assign(client, "NEGATEFALLDAMAGE", GLFW.GLFW_KEY_L, false);
        assign(client, "NEGATEFALLDAMAGE", GLFW.GLFW_KEY_ESCAPE, false);
        if (slot == 0) {
            add("native Lava toggle stores explicit false before world save", () -> click(icon(client, "LAVAREPAIR"), 0),
                    () -> !flag(feet(client), "lavarepair_toggle", true));
            assign(client, "LAVAREPAIR", GLFW.GLFW_KEY_V, false);
        } else {
            add("native Lava selection preserves explicit absence before save", () -> require(
                    !feet(client).has(JustDireAttributeTestSupport.component("lavarepair_toggle"))
                            && !feet(client).has(JustDireAttributeTestSupport.component("lavarepair_bindingmode")), "Native selection materialized absent Lava controls"));
        }
        close(client);
        add("explicit disabled mode/removal is independent of historical input preferences", () -> server(client, player -> {
            set(player.getItemBySlot(EquipmentSlot.FEET), "negatefalldamage_bindingmode", 0); player.inventoryMenu.broadcastChanges();
        }), () -> value(feet(client), "negatefalldamage_bindingmode") == 0);
        target(client);
        add("genuine native-configured input spends once before save", () -> {
            consumed = null; observedActivation = null; press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
        }, () -> consumed != null && charge(feet(client)) == 5_000 && timer(feet(client)) > 0);
        add("production action actually moved the world target and left sources untouched", () -> server(client, player -> {
            require(target.getDeltaMovement().horizontalDistance() > 0, "No actual lifecycle world effect");
            require(ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "Action changed source history");
            target.discard(); target = null;
        }));
        hold(client);
        add("packet-delivered spent GS has exact native HUD before save", () -> JustDireCooldownFeedbackTestSupport.assertHud(client, 1));
        add("funded incoming hit pays one root bill and normal packets preserve prior wear/history", () -> server(client, player ->
                JustDireArmorWearTestSupport.hit(player, 4_800, 0)), () -> charge(feet(client)) == 4_800
                && feet(client).getDamageValue() == 31 && ModContent.getMixedBootsData(feet(client)).orElseThrow().equals(provenance));
        add("unfunded incoming hit adds ordinary wear without a partial FE debit", () -> server(client, player -> {
            JustDireArmorWearTestSupport.extract(player, 4_700);
            JustDireArmorWearTestSupport.hit(player, 100, 2);
        }), () -> charge(feet(client)) == 100 && feet(client).getDamageValue() == 33);
        add("deplete the actually combat-spent root before saving powered Jump fallback", () -> server(client, player ->
                JustDireArmorWearTestSupport.extract(player, 96)), () -> charge(feet(client)) == 4 && feet(client).getDamageValue() == 33);
        lifecycleJump(client, 3, 2);
        add("store actual spent root and consumed/unconsumed production packets", () -> server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET);
            require(charge(root) == 4 && timer(root) > 0 && !flag(root, "stepheight_toggle", true), "Deferred permit acted before save");
            require(lifecycleRefundCredits.equals(root.get(JustDireRefundCompatibility.component())), "Native widgets/input/wear changed purchase credits");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            JustDireGroundStompLifecycleTestSupport.save(player, slot, root, consumed, held);
            player.inventoryMenu.broadcastChanges();
        }), () -> feet(client).isEmpty());
        add("stop deferring replies after storing this orientation", () -> holdReply = false);
    }

    private static void lifecycleRestart(Minecraft client, int slot) {
        // Rules/equipment can synchronize before Minecraft accepts damage from a newly joined client.
        boolean[] loaded = {false};
        add("wait for the actual client-loaded handshake before restart combat", () -> {}, () -> {
            if (loaded[0]) { return true; }
            server(client, player -> loaded[0] = player.connection.hasClientLoaded());
            return false;
        });
        add("tier preview and " + (slot == 0 ? "normal" : "shift-click") + " take preserve disk controls/resources", () -> server(client, player -> {
            try {
                player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                JustDireCooldownFeedbackTestSupport.begin(player);
                var root = JustDireGroundStompLifecycleTestSupport.storage(player, false).removeItemNoUpdate(slot);
                require(charge(root) == 4 && root.getDamageValue() == 33 && timer(root) > 0, "Disk resource/wear/timer mismatch");
                player.setItemSlot(EquipmentSlot.FEET, root);
                JustDireArmorWearTestSupport.hit(player, 4, 2); // The restored wallet, not the sources' historical 10k FE.
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                expected = JustDireGroundStompLifecycleTestSupport.smithing(player, root, slot == 1);
                JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
                player.getInventory().clearContent();
                provenance = ModContent.getMixedBootsData(expected).orElseThrow();
                player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
            } catch (Exception failure) { throw new AssertionError(failure); }
        }), () -> ModContent.getMixedBootsData(feet(client)).equals(java.util.Optional.ofNullable(provenance))
                && charge(feet(client)) == 4 && feet(client).getDamageValue() == 35
                && JustDireResourceTestSupport.handler(feet(client)).getCapacityAsInt() == 500_000);
        feedbackPause(client, 1);
        lavaTierLifecycle(client, slot);
        add("native donor recharge restores powered armor without repairing prior combat wear", () -> {
            depletedWearArmor = client.player.getAttributeValue(Attributes.ARMOR);
            server(client, player -> JustDireArmorWearTestSupport.recharge(player, 300));
        }, () -> charge(feet(client)) == 304 && feet(client).getDamageValue() == 35
                && client.player.getAttributeValue(Attributes.ARMOR) > depletedWearArmor);
        add("restored powered availability funds another hit without adding wear", () -> server(client, player ->
                JustDireArmorWearTestSupport.hit(player, 104, 0)), () -> charge(feet(client)) == 104 && feet(client).getDamageValue() == 35);
        add("actual XP Mending repairs only durability, not the spent wallet", () -> server(client, player ->
                JustDireArmorWearTestSupport.mend(player)), () -> charge(feet(client)) == 104 && feet(client).getDamageValue() < 35);
        add("retain the existing depleted Jump boundary after wear/Mending acceptance", () -> server(client, player ->
                JustDireArmorWearTestSupport.extract(player, 100)), () -> charge(feet(client)) == 4);
        lifecycleJump(client, 3, 2);
        lifecycleCharge(client, 5);
        lifecycleJump(client, 3, 3);
        // Keep the pre-existing paid cooldown refusal funded: lack of FE must not mask a lost timer.
        lifecycleCharge(client, 5_000);
        add("restarted binding modes/removal and switches are still live root controls", () -> require(
                JustDireSettingsCompatibility.groundStompBinding(feet(client), GLFW.GLFW_KEY_G, false)
                        && JustDireSettingsCompatibility.boundPassives(feet(client), 4, true).equals(List.of("stepheight", "jumpboost"))
                        && value(feet(client), "negatefalldamage_bindingmode") == 0
                        && noBinding(feet(client), "negatefalldamage")
                        && !flag(feet(client), "stepheight_toggle", true), "Restart lost passive modes/removal/preferences"));
        replaySaved(client, slot);
        add("positive persisted timer blocks a genuine configured key", () -> {
            before = feet(client).copy(); observedActivation = null; press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS);
        }, () -> observedActivation != null && observedActivation.prepared() && !observedActivation.accepted()
                && charge(feet(client)) == charge(before));
        add("persisted shared passive mouse works during the loaded cooldown without payment", () -> {
            observedActivation = null; before = feet(client).copy(); mousePress(4, GLFW.GLFW_PRESS);
        },
                () -> flag(feet(client), "stepheight_toggle", false) && !flag(feet(client), "jumpboost_toggle", true)
                        && charge(feet(client)) == 5_000 && timer(feet(client)) > 0);
        lifecycleJump(client, 3, 0);
        add("defer another genuine passive reply across ordinary timer equipment packets", () -> {
            holdReply = true; held = null; observedActivation = null; inputRoot = feet(client); before = inputRoot.copy(); mousePress(4, GLFW.GLFW_PRESS);
        }, () -> held != null);
        add("normal slot packets recreate the client root with unchanged passive settings", () -> {},
                () -> feet(client) != inputRoot && sameSettings(before, feet(client)));
        add("production pending passive intent follows unchanged settings across that slot packet", () -> {
            require(fieldStatic(JustDireAbilityInput.class, "pending") != null, "Ordinary synchronization cancelled free input intent");
            holdReply = false; JustDireAbilityInput.reply(observedActivation);
        }, () -> !flag(feet(client), "stepheight_toggle", true) && flag(feet(client), "jumpboost_toggle", false)
                && charge(feet(client)) == 5_000 && timer(feet(client)) > 0);
        open(client); select(client, 36);
        add("native tier-upgraded screen displays preserved controls", () -> require(slider(client, "GROUNDSTOMP").getValueInt() == 3
                && slider(client, "JUMPBOOST").getValueInt() == 3
                && ((Number) field(slider(client, "JUMPBOOST"), "maxValue")).intValue() == 5
                && value(feet(client), "groundstomp_custom_setting") == 1, "Tier UI reset controls/range"));
        add("new permanent tier admits five without restoring the historical slider", () -> slider(client, "JUMPBOOST").setValue(5),
                () -> value(feet(client), "jumpboost_value") == 5);
        close(client);
        lifecycleJump(client, 5, 5);
        lifecycleCharge(client, 4);
        lifecycleJump(client, 5, 2);
        lifecycleCharge(client, 5_000);
        add("loaded timer expires naturally instead of being reset by tier smithing", () -> {}, () -> timer(feet(client)) == -1);
        add("disk/tier GS expired with one ready cue and no lingering HUD", () -> {
            JustDireCooldownFeedbackTestSupport.assertCompletions(1, 0);
            JustDireCooldownFeedbackTestSupport.assertHud(client, 0);
        });
        replaySaved(client, slot);
        target(client);
        add("fresh persisted keyboard executes one upgraded action", () -> { consumed = null; press(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS); },
                () -> consumed != null && charge(feet(client)) == 0 && timer(feet(client)) > 0 && value(feet(client), "groundstomp_value") == 3);
        add("fresh action effect and source history verified on actual server", () -> server(client, player -> {
            require(target.getDeltaMovement().horizontalDistance() > 0
                    && ModContent.getMixedBootsData(player.getItemBySlot(EquipmentSlot.FEET)).orElseThrow().equals(provenance), "Restart action effect/provenance mismatch");
            target.discard(); target = null;
        }));
        open(client); select(client, 36); assign(client, "GROUNDSTOMP", 3, true); close(client);
        add("recharge preserves settings and waits for natural expiry", () -> server(client, player -> {
            set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", 6_000); player.inventoryMenu.broadcastChanges();
        }), () -> charge(feet(client)) == 6_000 && timer(feet(client)) == -1);
        add("fresh mouse binding uses production listener and one root bill", () -> mousePress(3, GLFW.GLFW_PRESS),
                () -> charge(feet(client)) == 1_000 && timer(feet(client)) > 0);
    }

    private static void lavaTierLifecycle(Minecraft client, int slot) {
        add("loaded Lava controls retain false/absence and keyboard binding", () -> {
            require(slot == 0 ? !flag(feet(client), "lavarepair_toggle", true) && bindingFor(feet(client), "lavarepair", GLFW.GLFW_KEY_V, false)
                    : !feet(client).has(JustDireAttributeTestSupport.component("lavarepair_toggle"))
                    && !feet(client).has(JustDireAttributeTestSupport.component("lavarepair_bindingmode")), "Restart/tier lost Lava preference ownership");
            lavaTooltip(client, slot != 0);
        });
        open(client); select(client, 36);
        add("restarted native Lava widget is intrinsic and has no slider", () -> require(hasIcon(client, "LAVAREPAIR")
                && slider(client, "LAVAREPAIR") == null, "Restart lost intrinsic native controls"));
        close(client);
        add("actual source-wise Blazegold tier preview/take removes Lava without resetting live state", () -> server(client, player -> {
            lavaBefore = player.getItemBySlot(EquipmentSlot.FEET).copy();
            var tier = JustDireGroundStompLifecycleTestSupport.removeLava(player, lavaBefore, slot != 0);
            require(!JustDireLavaRepairCompatibility.eligible(tier)
                    && JustDireSettingsCompatibility.metadata(tier, "lavarepair") == null, "Tier kept missing source eligibility");
            player.getInventory().clearContent(); player.setItemSlot(EquipmentSlot.FEET, tier); player.inventoryMenu.broadcastChanges();
        }), () -> !JustDireLavaRepairCompatibility.eligible(feet(client)));
        open(client); select(client, 36);
        add("ordinary tier slot packet removes only the Lava native widget/tooltip", () -> {
            require(!hasIcon(client, "LAVAREPAIR") && hasIcon(client, "GROUNDSTOMP"), "Client tier eligibility stale");
            var lines = feet(client).getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
            require(lines.stream().noneMatch(line -> line.getContents() instanceof TranslatableContents text
                    && text.getKey().equals("justdirethings.ability")), "Dormant Lava advertised as installed/available");
            JustDireGroundStompLifecycleTestSupport.sameControls(lavaBefore, feet(client), false);
        });
        close(client);
        add("actual Blazegold-to-Celestigem take changes incoming wear to the two-provider partial/empty rule", () -> server(client, player -> {
            JustDireArmorWearTestSupport.hit(player, 0, 0);
            JustDireArmorWearTestSupport.hit(player, 0, 0);
        }), () -> charge(feet(client)) == 0 && feet(client).getDamageValue() == lavaBefore.getDamageValue());
        add("real donor recharge after partial two-provider payment preserves wear", () -> server(client, player ->
                JustDireArmorWearTestSupport.recharge(player, 4)), () -> charge(feet(client)) == 4
                && feet(client).getDamageValue() == lavaBefore.getDamageValue());
        add("restore retained provider through production recomposition, not a fabricated downgrade recipe", () -> server(client, player -> {
            var live = player.getItemBySlot(EquipmentSlot.FEET);
            var retained = ModContent.getMixedBootsData(lavaBefore).orElseThrow();
            var restored = MixedBootsRecomposer.recompose(live, retained.left().source(), retained.right().source());
            JustDireGroundStompLifecycleTestSupport.sameControls(live, restored, true);
            require(charge(live) == charge(restored) && live.getDamageValue() == restored.getDamageValue(), "Provider restoration reset live resources");
            lavaBefore = restored.copy();
            player.setItemSlot(EquipmentSlot.FEET, restored); player.inventoryMenu.broadcastChanges();
        }), () -> JustDireLavaRepairCompatibility.eligible(feet(client)));
        add("restoring provider keeps exact Lava false/absence and unrelated state", () -> {
            JustDireGroundStompLifecycleTestSupport.sameControls(lavaBefore, feet(client), false);
            require(charge(feet(client)) == charge(lavaBefore), "Tier fixture restored/refilled FE");
        });
        if (slot == 0) {
            add("persisted Lava keyboard toggles after disk/tier synchronization without a bill", () -> press(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS),
                    () -> flag(feet(client), "lavarepair_toggle", false) && charge(feet(client)) == charge(lavaBefore));
            add("return persisted Lava preference to explicit false", () -> press(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS),
                    () -> !flag(feet(client), "lavarepair_toggle", true));
        }
    }

    private static void lifecycleCharge(Minecraft client, int energy) {
        add("ordinary lifecycle charge packet reaches powered Jump: " + energy, () -> {
            before = feet(client).copy();
            server(client, player -> {
                set(player.getItemBySlot(EquipmentSlot.FEET), "forge_energy", energy); player.inventoryMenu.broadcastChanges();
            });
        }, () -> {
            if (charge(feet(client)) != energy || wait <= 5) { return false; }
            JustDireGroundStompLifecycleTestSupport.sameControls(before, feet(client), false);
            require(before.getDamageValue() == feet(client).getDamageValue()
                    && ModContent.getMixedBootsData(before).equals(ModContent.getMixedBootsData(feet(client))), "Charge packet changed wear/history");
            return true;
        });
    }

    private static void lifecycleJump(Minecraft client, int configured, int usable) {
        add("persisted/tier Jump uses live FE and controls: configured=" + configured + ", usable=" + usable, () -> {
            assertPoweredJump(client, configured, usable);
            serverJump(client, usable);
        });
    }

    private static void serverJump(Minecraft client, int usable) {
        server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET); var snapshot = root.copy();
            // Isolate Jump's real server movement/fall-health companion from independently billed Negate.
            var jumpOnly = root.copy(); set(jumpOnly, "negatefalldamage_upgrade_installed", false);
            NeoForgeJustDireJumpTest.check(client.getSingleplayerServer(), jumpOnly, usable);
            require(ItemStack.matches(snapshot, root), "Server Jump observer changed equipped state/history");
        });
    }

    private static void feedbackPause(Minecraft client, int icons) {
        add("disk/tier equipment packets show exact native HUD without a load-only cue", () -> {
            JustDireCooldownFeedbackTestSupport.assertHud(client, icons);
            JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
        });
        add("pause the disk/tier root in ordinary inventory", () -> server(client, player -> {
            var root = player.getItemBySlot(EquipmentSlot.FEET);
            pausedFeedbackRoot = root.copy();
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.getInventory().setItem(0, root); player.inventoryMenu.broadcastChanges();
        }), () -> feet(client).isEmpty() && wait > 20);
        add("unequipped HUD is empty and the exact root remains paused without feedback", () -> {
            JustDireCooldownFeedbackTestSupport.assertHud(client, 0);
            server(client, player -> {
                var root = player.getInventory().getItem(0);
                require(ItemStack.matches(pausedFeedbackRoot, root), "Inventory pause changed persisted root/clocks");
                JustDireCooldownFeedbackTestSupport.assertCompletions(0, 0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.FEET, root); player.inventoryMenu.broadcastChanges();
            });
        }, () -> !feet(client).isEmpty() && wait > 5);
        add("re-equipped disk/tier root resumes the same native indicator", () -> JustDireCooldownFeedbackTestSupport.assertHud(client, icons));
    }

    private static void replaySaved(Minecraft client, int slot) {
        for (boolean unconsumed : new boolean[]{false, true}) {
            add("old JVM " + (unconsumed ? "unconsumed permit" : "consumed replay") + " rejects through real transport", () -> {
                try {
                    before = feet(client).copy(); observedActivation = null;
                    Network.getNetworkHandler().sendToServer(JustDireGroundStompLifecycleTestSupport.oldPacket(slot, unconsumed));
                } catch (Exception failure) { throw new AssertionError(failure); }
            }, () -> observedActivation != null && !observedActivation.prepared() && !observedActivation.accepted()
                    && charge(feet(client)) == charge(before) && sameSettings(before, feet(client)));
        }
    }

    private static boolean sameSettings(ItemStack first, ItemStack second) {
        return ItemStack.matches(JustDireActiveNetwork.passiveInputSnapshot(first), JustDireActiveNetwork.passiveInputSnapshot(second));
    }

    private static void hold(Minecraft client) {
        add("genuine passive input obtains a deferred unconsumed permit", () -> {
            holdReply = true; held = null; before = feet(client).copy(); mousePress(4, GLFW.GLFW_PRESS);
        }, () -> held != null && sameSettings(before, feet(client)) && charge(before) == charge(feet(client)));
    }

    private static void reconnect(Minecraft client) {
        open(client); select(client, 36);
        add("configure powered Jump before reconnect through the native slider", () -> {
            slider(client, "JUMPBOOST").setValue(1); slider(client, "JUMPBOOST").setValue(5);
        }, () -> value(feet(client), "jumpboost_value") == 5);
        assign(client, "JUMPBOOST", 4, true); close(client);
        lifecycleCharge(client, 4);
        lifecycleJump(client, 5, 0);
        hold(client);
        add("remember actual server/player and live resources before reconnect", () -> server(client, player -> {
            previousServer = client.getSingleplayerServer(); previousPlayer = player; expected = player.getItemBySlot(EquipmentSlot.FEET).copy();
            reconnectPassives = JustDireSettingsCompatibility.boundPassives(expected, 4, true);
        }));
        add("encode reconnect expectations before releasing the old registry context", () -> snapshotReconnect(client));
        add("disconnect from the real world with a pending passive intent", client::disconnectWithSavingScreen,
                () -> client.player == null && client.getSingleplayerServer() == null);
        add("production disconnect clears pending intent and native selection state", () -> require(
                fieldStatic(JustDireAbilityInput.class, "pending") == null && (int) fieldStatic(JustDireAbilityInput.class, "sequence") == 0
                        && fieldStatic(JustDireSettingsClient.class, "selected") == null
                        && fieldStatic(JustDireSettingsClient.class, "token").equals(JustDireActiveNetwork.NONE), "Disconnect retained client permissions"));
        add("rejoin the same saved world in this JVM", () -> client.createWorldOpenFlows().openWorld("jump", () -> client.setScreen(new TitleScreen())),
                () -> joined(client) && client.screen == null);
        add("decode expectations in the new connection registry context", () -> restoreReconnect(client));
        add("ordinary login equipment packets restore the saved root", () -> {},
                () -> sameSettings(expected, feet(client)) && charge(expected) == charge(feet(client)));
        lifecycleJump(client, 5, 0);
        add("first reconnect retains native spent-clock presentation through login packets", () -> {
            JustDireCooldownFeedbackTestSupport.assertHud(client, (timer(feet(client)) > 0 ? 1 : 0) + (earthquakeClock(feet(client)) != null ? 1 : 0));
            server(client, player -> JustDireCooldownFeedbackTestSupport.observedPlayer = player);
        });
        add("new server/player retain persistent controls/FE but not client intent", () -> {
            holdReply = false; observeReplies();
            require(sameSettings(expected, feet(client)) && charge(expected) == charge(feet(client)), "Reconnect changed persistent state");
            var oldClock = earthquakeClock(expected); var liveClock = earthquakeClock(feet(client));
            require(oldClock != null && liveClock != null && liveClock.ticks() > 0
                    && (long) liveClock.ticks() + (liveClock.active() ? 800L : 0L)
                    <= (long) oldClock.ticks() + (oldClock.active() ? 800L : 0L), "Reconnect lost/reset EQ lockout");
            server(client, player -> require(previousServer != client.getSingleplayerServer() && previousPlayer != player,
                    "Reconnect reused the old actual server/player"));
        });
        add("old unconsumed free permit rejects after real reconnect", () -> {
            before = feet(client).copy(); observedActivation = null; Network.getNetworkHandler().sendToServer(held);
        }, () -> observedActivation != null && !observedActivation.accepted() && sameSettings(before, feet(client)) && charge(before) == charge(feet(client)));
        add("fresh mouse after reconnect creates a new session and toggles supported passives", () -> {
            before = feet(client).copy(); mousePress(4, GLFW.GLFW_PRESS);
        }, () -> !reconnectPassives.isEmpty() && reconnectPassives.stream().allMatch(name ->
                flag(feet(client), name + "_toggle", true) != flag(before, name + "_toggle", true)) && charge(feet(client)) == charge(before));
        open(client); select(client, 36);
        add("fresh native settings selection after reconnect still edits the actual root", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> !flag(feet(client), "groundstomp_toggle", true));
        add("retain a genuine live native selection token before a second disconnect", () -> {
            require(fieldStatic(JustDireSettingsClient.class, "selected") == client.screen
                    && !fieldStatic(JustDireSettingsClient.class, "token").equals(JustDireActiveNetwork.NONE), "No live native selection to invalidate");
            stale = new JustDireActiveNetwork.SettingsRequest(((AbstractContainerScreen<?>) client.screen).getMenu().containerId,
                    36, (int) fieldStatic(JustDireSettingsClient.class, "selection"), (int) fieldStatic(JustDireSettingsClient.class, "request") + 1,
                    (UUID) fieldStatic(JustDireSettingsClient.class, "token"), JustDireActiveNetwork.TOGGLE, "groundstomp", 0, -1, false, true);
            snapshotReconnect(client);
        });
        add("disconnect with the genuine native selection still open", client::disconnectWithSavingScreen,
                () -> client.player == null && client.getSingleplayerServer() == null);
        add("production disconnect invalidates a previously live selection token", () -> require(
                fieldStatic(JustDireSettingsClient.class, "selected") == null
                        && fieldStatic(JustDireSettingsClient.class, "token").equals(JustDireActiveNetwork.NONE)
                        && (int) fieldStatic(JustDireSettingsClient.class, "selection") == 0, "Disconnect retained live menu permission"));
        add("rejoin again without copying the world", () -> client.createWorldOpenFlows().openWorld("jump", () -> client.setScreen(new TitleScreen())),
                () -> joined(client) && client.screen == null);
        add("decode second reconnect expectations in its registry context", () -> restoreReconnect(client));
        add("second login equipment packets restore the saved root", () -> {},
                () -> sameSettings(expected, feet(client)) && charge(expected) == charge(feet(client)));
        add("second reconnect retains native spent-clock presentation through login packets", () -> {
            JustDireCooldownFeedbackTestSupport.assertHud(client, (timer(feet(client)) > 0 ? 1 : 0) + (earthquakeClock(feet(client)) != null ? 1 : 0));
            server(client, player -> JustDireCooldownFeedbackTestSupport.observedPlayer = player);
        });
        add("second rejoin retains disabled control and the same resource balance", () -> {
            observeReplies(); require(sameSettings(expected, feet(client)) && charge(expected) == charge(feet(client)), "Menu disconnect changed root state");
        });
        open(client); select(client, 36);
        add("old selection token cannot toggle the new genuine menu/root", () -> {
            before = feet(client).copy(); server(client, player -> require(!JustDireActiveNetwork.edit(player, stale).accepted(),
                    "Reconnect resurrected old native selection permission"));
        }, () -> wait > 8 && sameSettings(before, feet(client)));
        add("fresh native token can re-enable the root after old-token rejection", () -> click(icon(client, "GROUNDSTOMP"), 0),
                () -> flag(feet(client), "groundstomp_toggle", false));
        close(client);
        lifecycleCharge(client, 5);
        lifecycleJump(client, 5, 0);
        add("fresh persisted mouse restores powered Jump after reconnect without a bill", () -> mousePress(4, GLFW.GLFW_PRESS),
                () -> flag(feet(client), "jumpboost_toggle", false) && charge(feet(client)) == 5);
        lifecycleJump(client, 5, 5);
    }

    private static void snapshotReconnect(Minecraft client) {
        reconnectExpected = ItemStack.CODEC.encodeStart(client.player.registryAccess().createSerializationContext(JsonOps.INSTANCE), feet(client)).getOrThrow();
    }

    private static void restoreReconnect(Minecraft client) {
        // Dynamic tag/holder identity belongs to a connection. Compare decoded values in the
        // current registry, not old-world Java holder instances that happen to print the same ID.
        expected = ItemStack.CODEC.parse(client.player.registryAccess().createSerializationContext(JsonOps.INSTANCE), reconnectExpected).getOrThrow();
    }

    private static void assign(Minecraft client, String name, int code, boolean mouse) {
        reveal(client, name);
        add("native capture persists " + name + " " + code + "/" + mouse, () -> {
            click((Button) maps(client, "bindingButtons").get(icon(client, name)), 0);
            require((boolean) field(client.screen, "bindingEnabled"), "Native capture did not start for " + name);
            if (mouse) { client.screen.mouseClicked(mouse(1, 1, code), false); }
            else { client.screen.keyPressed(new KeyEvent(code, 0, 0)); }
            require(!(boolean) field(client.screen, "bindingEnabled"), "Native capture did not end for " + name);
        }, () -> code == GLFW.GLFW_KEY_ESCAPE ? noBinding(feet(client), name.toLowerCase(java.util.Locale.ROOT))
                : bindingFor(feet(client), name.toLowerCase(java.util.Locale.ROOT), code, mouse));
    }

    private static boolean noBinding(ItemStack root, String name) {
        return root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of()).stream()
                .noneMatch(record -> fieldAccessor(record, "abilityName").equals(name));
    }

    private static void target(Minecraft client) {
        add("create a real non-hostile world target for production input", () -> server(client, player -> {
            target = EntityType.COW.create(player.level(), EntitySpawnReason.COMMAND);
            require(target != null, "Missing lifecycle target");
            target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
            target.setPos(player.getX() + 2, player.getY(), player.getZ());
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            require(player.level().addFreshEntity(target), "Lifecycle target not added");
        }));
    }

    private static ItemStack boot(String tier, int charge) {
        var boot = NeoForgeJustDireResourcesTest.boots(tier, charge);
        for (String name : List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "earthquake")) { set(boot, name + "_upgrade_installed", true); }
        bind(boot, GLFW.GLFW_KEY_G, false, true); return boot;
    }
    private static void setup(Minecraft client, boolean mixed, boolean inverse, int charge) {
        add("prepare " + (mixed ? "mixed " + inverse : "native control"), () -> server(client, player -> {
            var nativeBoot = boot("eclipsealloy", charge);
            var pair = mixed ? MixedBootsFactory.createPair(nativeBoot, boot("blazegold", 0)) : null;
            expected = mixed ? (inverse ? pair.inverse() : pair.primary()) : nativeBoot;
            if (mixed) { provenance = ModContent.getMixedBootsData(expected).orElseThrow(); }
            // Longer real cooldown flights must not race natural slime attacks/death in the copied flat world.
            client.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
        }), () -> client.screen == null && ItemStack.matches(expected, feet(client)));
    }
    private static boolean bindingFor(ItemStack root, String name, int key, boolean mouse) {
        return root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of()).stream()
                .anyMatch(record -> fieldAccessor(record, "abilityName").equals(name)
                        && (int) fieldAccessor(record, "key") == key && (boolean) fieldAccessor(record, "isMouse") == mouse);
    }

    private static void open(Minecraft client) {
        add("wait for native settings key context", () -> {}, () -> client.screen == null && key("toolUI").getKeyConflictContext().isActive());
        add("native settings key opens the native menu", () -> KeyMapping.click(key("toolUI").getKey()),
                () -> client.screen instanceof AbstractContainerScreen<?> && client.screen.getClass().getName().equals("com.direwolf20.justdirethings.client.screens.ToolSettingScreen"));
    }
    private static void close(Minecraft client) { add("close native menu", () -> client.screen.onClose(), () -> client.screen == null); }
    private static void select(Minecraft client, int index) {
        select(client, index, true);
    }
    private static void select(Minecraft client, int index, boolean mixed) {
        select(client, index, mixed, 0);
    }
    private static void select(Minecraft client, int index, boolean mixed, int button) {
        add("hover actual inventory slot " + index, () -> {
            var screen = (AbstractContainerScreen<?>) client.screen;
            var slot = screen.getMenu().slots.stream().filter(s -> s.getSlotIndex() == index).findFirst().orElseThrow();
            move(client, (int) field(screen, "leftPos") + slot.x + 8, (int) field(screen, "topPos") + slot.y + 8);
        }, () -> field(client.screen, "hoveredSlot") instanceof Slot slot && slot.getSlotIndex() == index);
        add("native slot click selects live mixed root", () -> {
            var slot = (Slot) field(client.screen, "hoveredSlot");
            client.screen.mouseClicked(mouse((int) field(client.screen, "leftPos") + slot.x + 8,
                    (int) field(client.screen, "topPos") + slot.y + 8, button), false);
        }, () -> field(client.screen, "tool") == client.player.getInventory().getItem(index)
                && (mixed ? !((UUID) fieldStatic(JustDireSettingsClient.class, "token")).equals(new UUID(0, 0))
                : ((UUID) fieldStatic(JustDireSettingsClient.class, "token")).equals(new UUID(0, 0))));
    }
    private static void reveal(Minecraft client, String name) {
        add("hover native " + name + " icon", () -> move(client, icon(client, name).getX() + 8, icon(client, name).getY() + 8),
                () -> field(client.screen, "hoveredSlot") == null);
        add("native right-click reveals detail widgets", () -> {
            var icon = icon(client, name);
            if (field(client.screen, "shownAbilityButton") != icon) {
                client.screen.mouseClicked(mouse(icon.getX() + 8, icon.getY() + 8, 1), false);
            }
        }, () -> field(client.screen, "shownAbilityButton") == icon(client, name));
    }
    private static void assertInternalSlots(Object menu) {
        try {
            var generator = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("justdirethings", "pocket_generator")));
            var refresh = menu.getClass().getMethod("refreshSlots", ItemStack.class);
            refresh.invoke(menu, generator);
            require(!((List<?>) field(menu, "dynamicSlots")).isEmpty(), "Native internal slots missing");
            refresh.invoke(menu, ItemStack.EMPTY);
            require(field(menu, "componentItemHandler") == null && ((List<?>) field(menu, "dynamicSlots")).isEmpty(), "Empty refresh retained slots");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static KeyMapping key(String name) {
        try { return (KeyMapping) Class.forName("com.direwolf20.justdirethings.client.KeyBindings").getField(name).get(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void move(Minecraft client, double x, double y) {
        double scale = client.getWindow().getGuiScale();
        try {
            var method = client.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class); method.setAccessible(true);
            method.invoke(client.mouseHandler, client.getWindow().handle(), x * scale, y * scale);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    private static void click(Button button, int action) { require(button.mouseClicked(mouse(button.getX() + 8, button.getY() + 8, action), false), "Native button ignored click"); }
    private static void press(int key, int action) { NeoForge.EVENT_BUS.post(new InputEvent.Key(new KeyEvent(key, 0, 0), action)); }
    private static void mousePress(int button, int action) { NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(new MouseButtonInfo(button, 0), action)); }
    private static String changedComponents(ItemStack expected, ItemStack actual) {
        if (expected == null) { return "no snapshot"; }
        if (expected.getItem() != actual.getItem() || expected.getCount() != actual.getCount()) {
            return expected + " -> " + actual;
        }
        var changes = new StringBuilder();
        for (var component : expected.getComponents()) {
            if (!java.util.Objects.equals(component.value(), actual.get(component.type()))) {
                changes.append(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()))
                        .append(": ").append(component.value()).append(" -> ").append(actual.get(component.type())).append(';');
            }
        }
        for (var component : actual.getComponents()) {
            if (!expected.has(component.type())) { changes.append(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type())).append(" added;"); }
        }
        return changes.toString();
    }
    private static Object fieldAccessor(Object record, String name) {
        try { return record.getClass().getMethod(name).invoke(record); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Map<?, ?> maps(Minecraft client, String name) { return (Map<?, ?>) field(client.screen, name); }
    private static Button icon(Minecraft client, String name) {
        return (Button) maps(client, "buttonToAbilityMap").entrySet().stream().filter(e -> e.getValue().toString().equals(name)).findFirst().orElseThrow().getKey();
    }
    private static ExtendedSlider slider(Minecraft client, String name) { return (ExtendedSlider) maps(client, "sliders").get(icon(client, name)); }
    private static ItemStack feet(Minecraft client) { return client.player == null ? ItemStack.EMPTY : client.player.getItemBySlot(EquipmentSlot.FEET); }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static int value(ItemStack root, String path) { return root.getOrDefault(JustDireAttributeTestSupport.<Integer>component(path), -1); }
    private static boolean flag(ItemStack root, String path, boolean fallback) { return root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(path), fallback); }
    private static int timer(ItemStack root) {
        try {
            var records = root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_cooldowns"), List.of());
            for (var record : records) {
                if (record.getClass().getMethod("abilityName").invoke(record).equals("groundstomp")) {
                    return (int) record.getClass().getMethod("cooldownTicks").invoke(record);
                }
            }
            return -1;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void bind(ItemStack root, int key, boolean mouse, boolean equipped) {
        try {
            set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                    .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", key, mouse, equipped)));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static int bound(ItemStack root) {
        try {
            for (var record : root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of())) {
                if (record.getClass().getMethod("abilityName").invoke(record).equals("groundstomp")) {
                    return (int) record.getClass().getMethod("key").invoke(record);
                }
            }
            return -1;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static boolean mouseBound(ItemStack root) {
        return root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of()).stream()
                .anyMatch(record -> fieldAccessor(record, "abilityName").equals("groundstomp") && (boolean) fieldAccessor(record, "isMouse"));
    }
    private static Object fieldStatic(Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object field(Object owner, String name) {
        for (var type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException absent) { }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("Missing field: " + name);
    }
    private static void server(Minecraft client, Consumer<ServerPlayer> task) {
        var id = client.player.getUUID(); var server = client.getSingleplayerServer(); require(server != null, "No server");
        job = server.submit(() -> { var player = server.getPlayerList().getPlayer(id); require(player != null, "No player"); task.accept(player); });
    }
    private static <T> void set(ItemStack root, String path, T value) { JustDireAttributeTestSupport.set(root, path, value); }
    private static void add(String name, Runnable start) { add(name, start, () -> true); }
    private static void add(String name, Runnable start, BooleanSupplier done) { steps.add(new Step(name, start, done)); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private record Step(String name, Runnable start, BooleanSupplier done, int timeout) {
        Step(String name, Runnable start, BooleanSupplier done) { this(name, start, done, 600); }
    }
    private static void add(String name, Runnable start, BooleanSupplier done, int timeout) { steps.add(new Step(name, start, done, timeout)); }
}
