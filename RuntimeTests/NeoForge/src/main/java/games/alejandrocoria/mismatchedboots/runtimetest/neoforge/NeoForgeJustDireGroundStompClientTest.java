package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsScreenAccess;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;
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
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static Future<?> job;
    private static boolean initialized, started, finished;
    private static int wait;
    private static ItemStack expected, before;
    private static Object provenance;
    private static JustDireActiveNetwork.SettingsRequest stale;

    private NeoForgeJustDireGroundStompClientTest() {}

    static void tick(Minecraft client) {
        if (finished || client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
        try {
            if (!initialized) { initialize(client); initialized = true; }
            if (job != null) { if (!job.isDone()) { return; } job.get(); job = null; }
            Step step = steps.peek();
            if (step == null) {
                // Let final close/ordinary slot packets settle before stopping the local channel.
                if (++wait <= 20) { return; }
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt", report + "production native UI/client keyboard/mouse/server transport; synthetic events, no physical/manual acceptance\n");
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", VARIANT + "\n"); finished = true;
            } else if (!started) { step.start.run(); started = true; wait = 0; }
            else if (step.done.getAsBoolean()) { report.append(step.name).append('\n'); steps.remove(); started = false; wait = 0; }
            else if (++wait > 600) { throw new AssertionError("Timed out: " + step.name
                    + "; charge=" + charge(feet(client)) + ", timer=" + timer(feet(client))
                    + "; changed components=" + changedComponents(before, feet(client))); }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, new AssertionError("Step: " + (steps.peek() == null ? "finish" : steps.peek().name), failure)); finished = true; }
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
        if (VARIANT.equals("hooksMissing") || VARIANT.equals("apiMissing")) {
            unavailable(client); return;
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
                require(names.size() == 4 && names.containsAll(List.of("GROUNDSTOMP", "STEPHEIGHT", "JUMPBOOST", "NEGATEFALLDAMAGE")), "Unsupported/missing abilities: " + names);
                require(maps(client, "sliders").size() == 2 && ((Number) field(slider(client, "GROUNDSTOMP"), "maxValue")).doubleValue() == 5
                        && ((Number) field(slider(client, "JUMPBOOST"), "maxValue")).doubleValue() == 2 && charge(feet(client)) == 0, "Per-ability tier ranges wrong");
                require(maps(client, "bindingButtons").size() == 1, "Passive custom bindings advertised");
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
            add("ordinary mouse clicks cannot bind or end native capture", () -> {
                click((Button) maps(client, "bindingButtons").get(icon(client, "GROUNDSTOMP")), 0);
                require(client.screen.mouseClicked(mouse(1, 1, 0), false)
                        && client.screen.mouseClicked(mouse(1, 1, 1), false)
                        && client.screen.mouseClicked(mouse(1, 1, 8), false), "Unsupported capture mouse was not consumed");
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
            set(other, "groundstomp_value", 1); player.getInventory().setItem(9, other);
            player.inventoryMenu.broadcastChanges(); expected = other.copy(); before = player.getItemBySlot(EquipmentSlot.FEET).copy();
        }), () -> ItemStack.matches(expected, client.player.getInventory().getItem(9)));
        select(client, 9);
        add("native tool switch uses the new root and its own tier range", () -> {
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
        select(client, 10, false);
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
        });
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
        add("hover actual inventory slot " + index, () -> {
            var screen = (AbstractContainerScreen<?>) client.screen;
            var slot = screen.getMenu().slots.stream().filter(s -> s.getSlotIndex() == index).findFirst().orElseThrow();
            move(client, (int) field(screen, "leftPos") + slot.x + 8, (int) field(screen, "topPos") + slot.y + 8);
        }, () -> field(client.screen, "hoveredSlot") instanceof Slot slot && slot.getSlotIndex() == index);
        add("native slot click selects live mixed root", () -> {
            var slot = (Slot) field(client.screen, "hoveredSlot");
            client.screen.mouseClicked(mouse((int) field(client.screen, "leftPos") + slot.x + 8,
                    (int) field(client.screen, "topPos") + slot.y + 8, 0), false);
        }, () -> mixed ? !((UUID) fieldStatic(JustDireSettingsClient.class, "token")).equals(new UUID(0, 0))
                : ((UUID) fieldStatic(JustDireSettingsClient.class, "token")).equals(new UUID(0, 0))
                && field(client.screen, "tool") == client.player.getInventory().getItem(index));
    }
    private static void reveal(Minecraft client, String name) {
        add("hover native " + name + " icon", () -> move(client, icon(client, name).getX() + 8, icon(client, name).getY() + 8),
                () -> field(client.screen, "hoveredSlot") == null);
        add("native right-click reveals detail widgets", () -> {
            var icon = icon(client, name); client.screen.mouseClicked(mouse(icon.getX() + 8, icon.getY() + 8, 1), false);
        });
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
    private static ItemStack feet(Minecraft client) { return client.player.getItemBySlot(EquipmentSlot.FEET); }
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
    private record Step(String name, Runnable start, BooleanSupplier done) {}
}
