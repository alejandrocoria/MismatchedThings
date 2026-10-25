package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipState;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.clear;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.install;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.paidPair;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Connected production tooltip acceptance; independent native controls and read-only observation. */
final class AetherIITooltipClientTest {
    private static final String OUTPUT = "aether-ii-tooltip";
    private static final boolean ABSENT = Boolean.getBoolean("mismatchedboots.runtimetest.aetherTooltipAbsent");
    private static final StringBuilder REPORT = new StringBuilder();
    private static final JsonArray CAPTURES = new JsonArray();
    private static List<ItemStack> serverSamples;
    private static Future<String> job;
    private static JsonObject expected;
    private static int ticks, scene = AetherTooltipFaultTest.active() ? 3 : 0, phase, settled, screenshots;
    private static boolean finished, backupConfirmed, screenshotPending, screenshotDone;
    private static Throwable screenshotFailure;
    private static CompletableFuture<Void> reload;
    private static Scene screen;
    private static int captureCount;
    private static AetherTooltipTestControl.Capture dualActive;
    private static ItemStack dualRoot;
    private static List<Component> burrukaiActive;

    private AetherIITooltipClientTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        if (!ABSENT) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AetherSourceCharmTest::observe);
        }
        if (!ABSENT && AetherTooltipTestControl.VARIANT.equals("live")) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AetherTooltipLifecycleTest::observe);
        }
    }

    private static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3000, "Aether tooltip timeout scene=" + scene + " phase=" + phase + "; see waiting marker");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (!backupConfirmed) {
                    backupConfirmed = true;
                    backup.children().stream().filter(child -> child instanceof Button button
                                  && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                          .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                }
                return;
            }
            if (phase == 4) {
                if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                    client.setScreen(null);
                }
                if (AetherTooltipLifecycleTest.tick(client, REPORT)) {
                    complete(client);
                }
                return;
            }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                client.setScreen(null);
            }
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Tooltip client not hidden/muted");
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                job = server.submit(() -> {
                    try {
                        return seed(server.getPlayerList().getPlayer(id));
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
                phase = 1;
                return;
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                expected = JsonParser.parseString(job.get()).getAsJsonObject();
                job = null;
                settled = 0;
            }
            if (phase == 1) {
                if (!(client.player.containerMenu instanceof ChestMenu) || !received(client)) {
                    if (ticks % 100 == 0) {
                        NetworkAcceptanceFiles.write(OUTPUT + "-waiting.txt",
                                                     "scene=" + scene + " menu=" + client.player.containerMenu.getClass().getName()
                                                             + " feet=" + AetherTooltipTestControl.encoded(client,
                                                                                                           client.player.getItemBySlot(EquipmentSlot.FEET)));
                    }
                    return;
                }
                if (++settled < 15) {
                    return;
                }
                if (ABSENT) {
                    var root = sample(client, 0);
                    var lines = capture(client, root, TooltipFlag.NORMAL, false).lines();
                    require(lines.stream().noneMatch(line -> translated(line, "item.aether_ii.")), "Absent presentation emitted Aether text");
                    complete(client);
                    return;
                }
                if (AetherTooltipFaultTest.active() && scene == 4) {
                    AetherTooltipFaultTest.verify(client, java.util.stream.IntStream.range(0, 33).mapToObj(i -> sample(client, i)).toList(), REPORT);
                    client.getLanguageManager().setSelected("es_es");
                    reload = client.reloadResourcePacks();
                    phase = 5;
                    return;
                }
                runScene(client);
                if (++scene < 27) {
                    configure(client);
                    return;
                }
                details(client);
                phase = 2;
                client.options.guiScale().set(2);
                client.resizeGui();
                GLFW.glfwSetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowSize(client.getWindow().handle(), 1920, 1080);
                screen = new Scene(sample(client, 24), true);
                client.setScreen(screen);
                return;
            }
            if (phase == 2) {
                if (!image(client)) {
                    return;
                }
                if (++screenshots == 1) {
                    screen = new Scene(sample(client, 5), false);
                    client.setScreen(screen);
                    return;
                }
                if (screenshots == 2) {
                    screen = new Scene(sample(client, 30), false);
                    client.setScreen(screen);
                    return;
                }
                if (screenshots == 3) {
                    screen = new Scene(sample(client, 31), true);
                    client.setScreen(screen);
                    return;
                }
                if (screenshots == 4) {
                    screen = new Scene(dualRoot, true);
                    screen.frozen = dualActive;
                    client.setScreen(screen);
                    return;
                }
                if (screenshots == 5) {
                    GLFW.glfwSetWindowSize(client.getWindow().handle(), 1280, 720);
                    screen = new Scene(dualRoot, true);
                    screen.frozen = dualActive;
                    client.setScreen(screen);
                    return;
                }
                client.setScreen(null);
                client.getLanguageManager().setSelected("es_es");
                reload = client.reloadResourcePacks();
                phase = 3;
                return;
            }
            if (phase == 3 && reload.isDone()) {
                reload.get();
                details(client);
                REPORT.append("Resource reload es_es: native fallback and repeated immutable production text/source graphics retained\n");
                if (AetherTooltipTestControl.VARIANT.equals("live")) {
                    AetherTooltipLifecycleTest.begin(java.util.stream.IntStream.range(0, 33).mapToObj(i -> sample(client, i).copy()).toList());
                    backupConfirmed = false;
                    phase = 4;
                } else {
                    complete(client);
                }
            }
            if (phase == 5 && reload.isDone()) {
                reload.get();
                AetherTooltipFaultTest.verify(client, java.util.stream.IntStream.range(0, 33).mapToObj(i -> sample(client, i)).toList(), REPORT);
                complete(client);
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(OUTPUT + "-partial-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.stop();
        }
    }

    private static String seed(ServerPlayer owner) throws Exception {
        clear(owner);
        owner.setNoGravity(true);
        owner.teleportTo(0, 90, 0);
        serverSamples = new ArrayList<>();
        if (ABSENT) {
            serverSamples.addAll(paidPair(owner, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)));
        } else {
            var api = new AetherIICompatibilityTest.NativeApi();
            for (String family : AetherTooltipTestControl.FAMILIES) {
                serverSamples.add(item(family + "_boots"));
            }
            for (int i = 0; i < 7; i++) {
                serverSamples.addAll(paidPair(owner, serverSamples.get(i), new ItemStack(Items.DIAMOND_BOOTS)));
            }
            serverSamples.add(paidPair(owner, item("burrukai_plate_boots"), item("burrukai_plate_boots")).getFirst()); // 21
            serverSamples.addAll(paidPair(owner, item("zanite_boots"), item("neptune_boots"))); // 22,23
            var first = install(owner, api, item("neptune_boots"), "health", 0, true);
            first = install(owner, api, first, "health", 1, true);
            var second = install(owner, api, item("neptune_boots"), "health", 0, true);
            // Synthetic unequal tier metadata isolates presentation, not paid reinforcement eligibility.
            var tier = Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier");
            first.set(component("reinforcement_tier"), tier.getField("FIRST").get(null));
            second.set(component("reinforcement_tier"), tier.getField("SECOND").get(null));
            serverSamples.addAll(paidPair(owner, first, second)); // 24,25
            var jdt = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("justdirethings:eclipsealloy_boots")).orElseThrow());
            serverSamples.addAll(paidPair(owner, first, jdt)); // 26,27
            serverSamples.addAll(paidPair(owner, first, new NeoForgeSilentGearTest.NativeApi().boot("iron"))); // 28,29
            var reinforced = AetherSourceCharmTest.reinforced(owner, api);
            serverSamples.add(reinforced); // 30: native paid reinforcement, not synthetic presentation metadata.
            serverSamples.addAll(paidPair(owner, reinforced, new ItemStack(Items.DIAMOND_BOOTS))); // 31,32
        }
        var container = new SimpleContainer(54);
        for (int i = 0; i < serverSamples.size(); i++) {
            container.setItem(i, serverSamples.get(i));
        }
        owner.openMenu(new SimpleMenuProvider((id, inventory, player) -> ChestMenu.sixRows(id, inventory, container),
                                              Component.literal("Production Aether tooltips")));
        return prepare(owner);
    }

    private static String prepare(ServerPlayer owner) throws Exception {
        clear(owner);
        if (!ABSENT) {
            var api = new AetherIICompatibilityTest.NativeApi();
            api.accessories(owner).clearContent();
            String family = scene < 21 ? AetherTooltipTestControl.FAMILIES.get(scene / 3)
                                       : scene == 22 || scene == 23 ? "zanite" : "burrukai_plate";
            int index = scene < 21 ? (scene % 3 == 0 ? scene / 3 : 7 + (scene / 3) * 2 + scene % 3 - 1)
                                   : scene == 21 ? 21 : scene < 24 ? scene : 9;
            owner.setItemSlot(EquipmentSlot.FEET, serverSamples.get(index).copy());
            if (!family.equals("sentry")) {
                owner.setItemSlot(EquipmentSlot.HEAD, item(family + "_helmet"));
                if (scene < 24 || scene == 25) {
                    owner.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate"));
                }
            }
            if (scene == 21) {
                owner.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
                owner.setItemSlot(EquipmentSlot.CHEST, item("burrukai_plate_chestplate"));
            }
            if (scene == 22 || scene == 23) {
                owner.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
                owner.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
                owner.setItemSlot(EquipmentSlot.LEGS, item("neptune_leggings"));
                Class<?> slots = Class.forName("com.aetherteam.aetherii.inventory.container.AccessoryContainer$SlotType");
                int handwear = ((int[]) slots.getMethod("getIndex").invoke(slots.getField("HANDWEAR").get(null)))[0];
                api.accessories(owner).setItem(handwear, item("neptune_gloves"));
            }
            if (scene == 24) {
                owner.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
            } // Two pieces, set inactive.
        }
        var result = new JsonObject();
        var roots = new JsonArray();
        var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var sample : serverSamples) {
            roots.add(ItemStack.CODEC.encodeStart(ops, sample).getOrThrow());
        }
        result.add("samples", roots);
        if (!ABSENT) {
            result.add("feet", ItemStack.CODEC.encodeStart(ops, owner.getItemBySlot(EquipmentSlot.FEET)).getOrThrow());
        }
        owner.inventoryMenu.broadcastChanges(); // The open chest does not include armor slots; use ordinary slot packets for the wearer's inventory menu.
        return result.toString();
    }

    private static boolean received(Minecraft client) {
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < expected.getAsJsonArray("samples").size(); i++) {
            var root = ItemStack.CODEC.parse(ops, expected.getAsJsonArray("samples").get(i)).getOrThrow();
            if (!ItemStack.matches(root, sample(client, i))) {
                if (ticks % 100 == 0) {
                    NetworkAcceptanceFiles.write(OUTPUT + "-mismatch.txt",
                                                 "slot=" + i + "\nexpected=" + AetherTooltipTestControl.encoded(client, root)
                                                         + "\nreceived=" + AetherTooltipTestControl.encoded(client, sample(client, i)));
                }
                return false;
            }
        }
        return ABSENT || ItemStack.matches(ItemStack.CODEC.parse(ops, expected.get("feet")).getOrThrow(),
                                           client.player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static void configure(Minecraft client) {
        var server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        job = server.submit(() -> {
            try {
                return prepare(server.getPlayerList().getPlayer(id));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    private static boolean attributeReplacement() {
        return !AetherTooltipTestControl.VARIANT.equals("attributechanged");
    }

    private static boolean tierReplacement() {
        return !AetherTooltipTestControl.VARIANT.equals("tierchanged");
    }

    private static void runScene(Minecraft client) throws Exception {
        var worn = client.player.getItemBySlot(EquipmentSlot.FEET);
        require(client.player.inventoryMenu.getSlot(8).getItem() == worn, "Worn inventory slot supplies a copy");
        capture(client, sample(client, 7), TooltipFlag.NORMAL, false); // Load the owned source scope, even in a native-control scene.
        require(AetherTooltipState.attributesReady() == attributeReplacement()
                        && AetherTooltipState.tiersReady() == tierReplacement(), "Wrong independent containment readiness");
        var nativeRows = nativeSetRows(client, TooltipFlag.NORMAL);
        int expectedRows = scene < 21 ? new int[]{0, 2, 3, 1, 1, 2, 0}[scene / 3] : scene == 21 ? 2 : scene < 24 ? 5 : scene == 25 ? 2 : 0;
        require(nativeRows.size() == expectedRows, "Native player set modifiers not synchronized, scene=" + scene);
        var actual = capture(client, worn, TooltipFlag.NORMAL, false);
        var copied = capture(client, worn.copy(), TooltipFlag.NORMAL, false);
        if (ModContent.isMixedBoots(worn) && attributeReplacement()) {
            require(actual.nativeAttributes() == 0 && copied.nativeAttributes() == 0, "Native added mixed/source values leaked");
            requireRows(actual.lines(), nativeRows, true);
            requireRows(copied.lines(), nativeRows, false);
            String family = scene < 21 ? AetherTooltipTestControl.FAMILIES.get(scene / 3)
                                       : scene == 21 || scene >= 24 ? "burrukai_plate" : "zanite";
            if (!family.equals("sentry")) {
                String key = item(family + "_boots").getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.2";
                var set = component("armor_set");
                @SuppressWarnings("unchecked") var tag = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) item(family + "_boots").get(set);
                int count = Math.min(3, new AetherIICompatibilityTest.NativeApi().count(client.player, tag));
                require(actual.lines().contains(Component.translatable(key, Component.literal(count + "/3")
                                                                                     .withStyle(count == 3
                                                                                                ? net.minecraft.ChatFormatting.WHITE
                                                                                                : net.minecraft.ChatFormatting.GRAY))),
                        "Live native count/style missing");
                require(copied.lines().contains(Component.translatable(key, Component.literal(count + "/3")
                                                                                     .withStyle(count == 3
                                                                                                ? net.minecraft.ChatFormatting.WHITE
                                                                                                : net.minecraft.ChatFormatting.GRAY))),
                        "Copied preview lost the player's actual native equipment count");
            }
            verifySourceSets(client, worn, actual.lines());
            verifySourceSets(client, worn.copy(), copied.lines());
            if (!nativeRows.isEmpty()) {
                int ordinary = index(actual.lines(), "item.modifiers.feet");
                int firstSet = actual.lines().size();
                int lastSet = -1;
                for (var row : nativeRows) {
                    int position = actual.lines().indexOf(row);
                    firstSet = Math.min(firstSet, position);
                    lastSet = Math.max(lastSet, position);
                }
                require(ordinary >= 0 && firstSet > ordinary + 1 && lastSet - firstSet + 1 == nativeRows.size(),
                        "Active set attributes are not together after ordinary root attributes");
                require(actual.lines().subList(ordinary + 1, firstSet).stream().allMatch(line -> translated(line, "attribute.modifier")
                                || translated(line, "neoforge.modifier.") || line.equals(AetherTooltipTestControl.PEER)),
                        "Non-attribute text separates ordinary attributes and active set attributes: " + actual.lines()
                                                                                                               .subList(ordinary + 1, firstSet));
            }
            if (!nativeRows.isEmpty()) {
                var oldDisplay = worn.get(DataComponents.TOOLTIP_DISPLAY);
                try {
                    for (var hidden : List.of(component("armor_set"), DataComponents.ATTRIBUTE_MODIFIERS)) {
                        worn.set(DataComponents.TOOLTIP_DISPLAY, AetherTooltipTestControl.display(worn).withHidden(hidden, true));
                        var hiddenLines = capture(client, worn, TooltipFlag.NORMAL, false).lines();
                        requireRows(hiddenLines, nativeRows, false);
                        // Source descriptions do not inherit the root's hidden set/attribute flags.
                        if (hidden == component("armor_set")) {
                            verifySourceSets(client, worn, hiddenLines);
                        }
                        if (oldDisplay == null) {
                            worn.remove(DataComponents.TOOLTIP_DISPLAY);
                        } else {
                            worn.set(DataComponents.TOOLTIP_DISPLAY, oldDisplay);
                        }
                    }
                } finally {
                    if (oldDisplay == null) {
                        worn.remove(DataComponents.TOOLTIP_DISPLAY);
                    } else {
                        worn.set(DataComponents.TOOLTIP_DISPLAY, oldDisplay);
                    }
                }
            }
        } else if (!ModContent.isMixedBoots(worn)) {
            require(actual.nativeAttributes() == expectedRows, "Native original presentation changed");
        }
        if (scene == 3) {
            burrukaiActive = List.copyOf(nativeRows);
        }
        if (attributeReplacement()) {
            if (scene == 24 || scene == 26) {
                requireRows(actual.lines(), burrukaiActive, false);
            }
            if (scene == 10 || scene == 11) {
                require(actual.lines()
                              .stream()
                              .noneMatch(line -> translated(line, "item.aether_ii.arkenium_boots.nitrogen_internals.ability.tooltip.1")),
                        "Arkenium promises unsupported mixed upgrades");
            }
            if (scene == 19 || scene == 20) {
                require(actual.lines().stream().anyMatch(line -> translated(line, "item.aether_ii.sentry_boots.nitrogen_internals.ability.tooltip.1"))
                                && actual.lines()
                                         .stream()
                                         .noneMatch(line -> translated(line, "item.aether_ii.sentry_boots.nitrogen_internals.ability.tooltip.2")),
                        "Sentry lost its standalone ability or gained a set counter");
            }
            if (scene == 21) {
                require(actual.lines()
                              .stream()
                              .filter(line -> translated(line, "item.aether_ii.burrukai_plate_boots.nitrogen_internals.ability.tooltip.1"))
                              .count() == 2,
                        "Each duplicate source should retain its own ability, without duplicating root bonuses");
            }
        }
        if (scene == 22) {
            dualRoot = worn.copy();
            dualActive = capture(client, worn, TooltipFlag.ADVANCED, false);
        }
        int matching = scene < 21 ? 7 + scene / 3 * 2 : 9;
        for (int index : new int[]{matching, matching + 1, 7 + ((scene + 1) % 7) * 2}) {
            var idle = capture(client, sample(client, index), TooltipFlag.NORMAL, false);
            if (attributeReplacement()) {
                require(idle.nativeAttributes() == 0, "Idle pair borrowed native source/root additions");
                requireRows(idle.lines(), nativeRows, false);
                // Focused fault variants verify their intentional text omissions in the next actual mixed scene.
                if (!AetherTooltipFaultTest.active()) {
                    verifySourceSets(client, sample(client, index), idle.lines());
                }
            }
        }
        if (scene == 3 && attributeReplacement()) {
            scopeProbes(client, nativeRows);
        }
        if (AetherTooltipTestControl.VARIANT.equals("chains") && ModContent.isMixedBoots(worn)) {
            require(actual.lines().stream().filter(AetherTooltipTestControl.PEER::equals).count() == 3,
                    "Local containment removed foreign listener additions");
            require(AetherTooltipTestControl.peers > 0, "Compatible argument chain never ran");
        }
        REPORT.append("SCENE ").append(scene).append(" nativePlayerRows=").append(nativeRows.size())
              .append(" nativeProducerAdded=").append(actual.nativeAttributes()).append(" mixed=").append(ModContent.isMixedBoots(worn))
              .append(" attributeReplacement=").append(attributeReplacement()).append('\n');
    }

    private static void scopeProbes(Minecraft client, List<Component> expectedNative) throws Exception {
        var root = sample(client, 9);
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            var expected = nativeSetRows(client, flag);
            AetherTooltipTestControl.nested = true;
            try {
                capture(client, root, flag, false);
                requireRows(AetherTooltipTestControl.nestedLines, expected, true);
            } finally {
                AetherTooltipTestControl.nested = false;
            }
            String before = AetherTooltipTestControl.encoded(client, root);
            AetherTooltipTestControl.throwing = true;
            try {
                capture(client, root, flag, false);
                throw new AssertionError("Controlled ordinary source exception did not propagate");
            } catch (AetherTooltipTestControl.DetailFailure expectedFailure) {
                require(before.equals(AetherTooltipTestControl.encoded(client, root)), "Failed details mutated history");
            } finally {
                AetherTooltipTestControl.throwing = false;
            }
            var after = capture(client, AetherTooltipTestControl.failedSource, flag, false);
            require(after.nativeAttributes() == expectedNative.size(), "Source identity leaked after an ordinary provider exception");
            requireRows(after.lines(), expected, true);
            AetherTooltipTestControl.failedSource = null;
        }
        REPORT.append(
                "SCOPE normal/advanced: nested foreign native tooltip retained; original provider exception propagates; same source afterwards has native values, no leaked containment\n");
    }

    @SuppressWarnings("unchecked")
    static List<Component> nativeSetRows(Minecraft client, TooltipFlag flag) throws Exception {
        var type = Class.forName("com.aetherteam.aetherii.mixin.mixins.common.accessor.AttributeMapAccessor");
        var map = (java.util.Map<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>,
                net.minecraft.world.entity.ai.attributes.AttributeInstance>) type.getMethod("aether_ii$getAttributes")
                                                                                 .invoke(client.player.getAttributes());
        var rows = new ArrayList<Component>();
        for (var attribute : map.entrySet()) {
            for (var modifier : attribute.getValue().getModifiers()) {
                if (modifier.id().getNamespace().equals("aether_ii") && modifier.id().getPath().startsWith("armor_set.ability.")) {
                    rows.add(attribute.getKey().value().toComponent(modifier, flag));
                }
            }
        }
        return rows;
    }

    private static void requireRows(List<Component> lines, List<Component> expected, boolean present) {
        for (Component row : expected) {
            require(lines.stream().filter(row::equals).count() == (present ? 1 : 0), "Wrong actual native player row ownership/duplication: " + row);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifySourceSets(Minecraft client, ItemStack root, List<Component> lines) throws Exception {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        int left = index(lines, "tooltip.mismatchedboots.mixed_boots.left");
        int right = index(lines, "tooltip.mismatchedboots.mixed_boots.right");
        int rootAttributes = index(lines, "item.modifiers.feet");
        for (int side = 0; side < 2; side++) {
            var source = side == 0 ? data.left().source() : data.right().source();
            if (!AetherTooltipTestControl.FAMILIES.stream().anyMatch(family -> source.is(item(family + "_boots").getItem()))) {
                continue;
            }
            var section = lines.subList((side == 0 ? left : right) + 1, side == 0 ? right : rootAttributes);
            String prefix = source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.";
            var set = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) source.get(component("armor_set"));
            if (!source.is(item("arkenium_boots").getItem())) {
                require(section.stream().filter(line -> translated(line, prefix + "1")).count() == 1, "Ability missing from its own source");
            }
            if (!source.is(item("sentry_boots").getItem())) {
                int count = Math.min(3, new AetherIICompatibilityTest.NativeApi().count(client.player, set));
                require(section.contains(Component.translatable(prefix + "2", Component.literal(count + "/3")
                                                                                       .withStyle(count == 3
                                                                                                  ? net.minecraft.ChatFormatting.WHITE
                                                                                                  : net.minecraft.ChatFormatting.GRAY))),
                        "Source count is not actual current native progress");
            }
            require(lines.stream().noneMatch(line -> line.equals(source.getItemName())), "Redundant root source-name heading remains");
        }
    }

    private static void details(Minecraft client) throws Exception {
        for (int i = 0; i < 33; i++) {
            ItemStack target = sample(client, i);
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var compact = capture(client, target, flag, false);
                var expanded = capture(client, target, flag, true);
                if (i < 26 || i > 27) {
                    require(compact.lines().stream().map(Component::copy).toList().equals(expanded.lines().stream().map(Component::copy).toList()),
                            "Unexpected Aether/SG Shift-only presentation");
                }
                AetherSourceCharmTest.verify(client, target, compact.lines());
                AetherSourceCharmTest.verify(client, target, expanded.lines());
                if (i == 24) {
                    var tiers = compact.lines().stream().filter(line -> translated(line, "aether_ii.tooltip.item.reinforcement")).toList();
                    require(tiers.size() == (tierReplacement() ? 2 : 1), "Wrong independent original tier placement/refusal");
                    if (tierReplacement()) {
                        int left = index(compact.lines(), "tooltip.mismatchedboots.mixed_boots.left");
                        int right = index(compact.lines(), "tooltip.mismatchedboots.mixed_boots.right");
                        require(compact.lines().indexOf(tiers.getFirst()) > left && compact.lines().indexOf(tiers.getFirst()) < right
                                        && compact.lines().indexOf(tiers.getLast()) > right, "Tier lost its original side");
                    }
                }
                if (i >= 26 && i < 30) {
                    String partner = i < 28 ? "justdirethings." : "item.silentgear.";
                    require(compact.lines().stream().anyMatch(line -> translated(line, partner)), "Existing JDT/SG sections missing");
                    if (i < 28) {
                        require(compact.lines().stream().filter(line -> translated(line, "justdirethings.festored")).count() == 1,
                                "JDT root FE duplicated/lost");
                    }
                }
                if (attributeReplacement() && ModContent.isMixedBoots(target)) {
                    verifySourceSets(client, target, compact.lines());
                }
            }
        }
        var root = sample(client, 24);
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        if (attributeReplacement()) {
            var left = data.left().source();
            left.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(component("armor_set"), true));
            var hidden = root.copy();
            hidden.set(ModContent.mixedBootsDataType().orElseThrow(),
                       new MixedBootsData(new BootHalf(left), data.right()));
            var lines = capture(client, hidden, TooltipFlag.NORMAL, false).lines();
            int right = index(lines, "tooltip.mismatchedboots.mixed_boots.right");
            require(lines.subList(0, right)
                         .stream()
                         .noneMatch(line -> translated(line, "item.aether_ii.neptune_boots.nitrogen_internals.ability.tooltip."))
                            && lines.subList(right, lines.size())
                                    .stream()
                                    .filter(line -> translated(line, "item.aether_ii.neptune_boots.nitrogen_internals.ability.tooltip."))
                                    .count() == 2,
                    "Source armor-set visibility hid the healthy other source or leaked its own ability/count");
        }
        for (boolean all : List.of(false, true)) {
            var left = data.left().source();
            var right = data.right().source();
            for (var source : List.of(left, right)) {
                source.set(DataComponents.TOOLTIP_DISPLAY, all ? new TooltipDisplay(true, new java.util.LinkedHashSet<>())
                                                               : TooltipDisplay.DEFAULT.withHidden(component("reinforcement_tier"), true));
            }
            var modified = root.copy();
            modified.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(left), new BootHalf(right)));
            var hiddenLines = capture(client, modified, TooltipFlag.ADVANCED, true).lines();
            AetherSourceCharmTest.verify(client, modified, hiddenLines);
            require(hiddenLines.stream()
                               .noneMatch(line -> translated(line, "aether_ii.tooltip.item.reinforcement")) || !tierReplacement(),
                    "Hidden source tiers still published");
        }
        var hidden = root.copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        var hiddenRootLines = capture(client, hidden, TooltipFlag.NORMAL, false).lines();
        require(hiddenRootLines.stream().noneMatch(line -> translated(line, "item.aether_ii.")), "Hidden root published capabilities");
        AetherSourceCharmTest.verify(client, hidden, hiddenRootLines);
        AetherSourceCharmTest.run(client, java.util.stream.IntStream.range(0, 33).mapToObj(i -> sample(client, i)).toList(), REPORT);
        REPORT.append(
                "DETAILS 33 native/mixed samples, four key/flag modes, independent tiers/visibility, JDT/SG and native source graphics; serialized root/history equality\n");
    }

    private static AetherTooltipTestControl.Capture capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean shift) throws Exception {
        List<Component> before = ABSENT ? List.of() : nativeSetRows(client, flag);
        ItemStack feet = client.player.getItemBySlot(EquipmentSlot.FEET);
        var captured = AetherTooltipTestControl.capture(client, stack, flag, shift);
        captureCount++;
        if (!ABSENT) {
            require(before.equals(nativeSetRows(client, flag)) && feet == client.player.getItemBySlot(EquipmentSlot.FEET),
                    "Hover changed native player attributes/equipment");
        }
        var json = new JsonObject();
        json.addProperty("scene", scene);
        json.addProperty("advanced", flag.isAdvanced());
        json.addProperty("shift", shift);
        json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("wornReference", stack == feet);
        json.addProperty("nativeAttributeCallbacks", captured.calls());
        json.addProperty("nativeAdded", captured.nativeAttributes());
        var lines = new JsonArray();
        for (var line : captured.lines()) {
            lines.add(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, line).getOrThrow());
        }
        json.add("lines", lines);
        CAPTURES.add(json);
        return captured;
    }

    private static ItemStack sample(Minecraft client, int index) {
        return client.player.containerMenu.getSlot(index).getItem();
    }

    private static boolean translated(Component line, String prefix) {
        if (line.getContents() instanceof TranslatableContents text && text.getKey().startsWith(prefix)) {
            return true;
        }
        return line.getSiblings().stream().anyMatch(child -> translated(child, prefix));
    }

    private static int index(List<Component> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (translated(lines.get(i), prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean image(Minecraft client) {
        if (screenshotFailure != null) {
            throw new AssertionError("Screenshot failed", screenshotFailure);
        }
        if (screenshotDone) {
            screenshotDone = false;
            return true;
        }
        if (screenshotPending || screen.frames < 5) {
            return false;
        }
        screenshotPending = true;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "tooltip-" + screenshots + ".png"));
                REPORT.append("FRAMEBUFFER ").append(image.getWidth()).append('x').append(image.getHeight()).append(" guiScale=")
                      .append(client.getWindow().getGuiScale()).append(" mixed=").append(screen.mixed)
                      .append(" frozenEquippedCapture=").append(screen.frozen != null).append('\n');
                screenshotDone = true;
            } catch (Throwable failure) {
                screenshotFailure = failure;
            } finally {
                screenshotPending = false;
            }
        });
        return false;
    }

    private static final class Scene extends Screen {
        private final ItemStack root;
        private final boolean mixed;
        private AetherTooltipTestControl.Capture frozen;
        private int frames;

        private Scene(ItemStack root, boolean mixed) {
            super(Component.literal("Production Aether tooltips"));
            this.root = root;
            this.mixed = mixed;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.text(font,
                          frozen != null ? "Frozen equipped production Zanite/Neptune tooltip" : mixed
                                                                                                 ? "Production Aether mixed text / source charms"
                                                                                                 : "Native original control",
                          20,
                          12,
                          0xFFFFFFFF,
                          false);
            try {
                var captured = frozen != null ? frozen : capture(Minecraft.getInstance(), root, TooltipFlag.ADVANCED, false);
                graphics.setTooltipForNextFrame(font, captured.lines(), Optional.empty(), root, 20, 40);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static void complete(Minecraft client) {
        REPORT.append("structuredCaptures=").append(captureCount).append(" absent=").append(ABSENT)
              .append(" variant=").append(AetherTooltipTestControl.VARIANT)
              .append("; production text/source graphics, immutable root/history/player state\n");
        NetworkAcceptanceFiles.write(OUTPUT + "-captures.json", CAPTURES.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n");
        finished = true;
        client.stop();
    }
}
