package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
import net.neoforged.neoforge.client.ClientHooks;
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

/** Measured baseline and disposable client candidate. No native gameplay is replaced by the spike. */
final class AetherTooltipClientTest {
    private static final String OUTPUT = "aether-ii-tooltip";
    private static final boolean ABSENT = Boolean.getBoolean("mismatchedboots.runtimetest.aetherTooltipAbsent");
    private static final StringBuilder REPORT = new StringBuilder();
    private static final JsonArray CAPTURES = new JsonArray();
    private static List<ItemStack> serverSamples;
    private static Future<String> job;
    private static JsonObject expected;
    private static int ticks, scene, phase, settled, screenshots;
    private static boolean finished, backupConfirmed, screenshotPending, screenshotDone;
    private static Throwable screenshotFailure;
    private static CompletableFuture<Void> reload;
    private static Scene screen;
    private static int captureCount;
    private static int borrowed;
    private static AetherTooltipSpike.Capture dualActive;
    private static ItemStack dualRoot;

    private AetherTooltipClientTest() {}

    static void register() {
        AetherTooltipSpike.register();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
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
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) { client.setScreen(null); }
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Tooltip client not hidden/muted");
                var server = client.getSingleplayerServer(); var id = client.player.getUUID();
                job = server.submit(() -> {
                    try { return seed(server.getPlayerList().getPlayer(id)); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }); phase = 1; return;
            }
            if (job != null) {
                if (!job.isDone()) { return; }
                expected = JsonParser.parseString(job.get()).getAsJsonObject(); job = null; settled = 0;
            }
            if (phase == 1) {
                if (!(client.player.containerMenu instanceof ChestMenu) || !received(client)) {
                    if (ticks % 100 == 0) {
                        NetworkAcceptanceFiles.write(OUTPUT + "-waiting.txt", "scene=" + scene + " menu=" + client.player.containerMenu.getClass().getName()
                                + " feet=" + AetherTooltipSpike.encoded(client, client.player.getItemBySlot(EquipmentSlot.FEET)));
                    }
                    return;
                }
                if (++settled < 15) { return; }
                if (ABSENT) {
                    var root = sample(client, 0);
                    require(capture(client, root, TooltipFlag.NORMAL, false, false).lines()
                            .equals(capture(client, root, TooltipFlag.NORMAL, false, true).lines()), "Absent presentation changed vanilla mixed");
                    complete(client); return;
                }
                runScene(client);
                if (++scene < 25) { configure(client); return; }
                details(client); phase = 2;
                client.options.guiScale().set(2); client.resizeGui();
                GLFW.glfwSetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowSize(client.getWindow().handle(), 1920, 1080);
                screen = new Scene(sample(client, 24), true); client.setScreen(screen); return;
            }
            if (phase == 2) {
                if (!image(client)) { return; }
                if (++screenshots == 1) {
                    screen = new Scene(sample(client, 24), false); client.setScreen(screen); return;
                }
                if (screenshots == 2) {
                    screen = new Scene(dualRoot, true); screen.frozen = dualActive; client.setScreen(screen); return;
                }
                if (screenshots == 3) {
                    GLFW.glfwSetWindowSize(client.getWindow().handle(), 1280, 720);
                    screen = new Scene(dualRoot, true); screen.frozen = dualActive; client.setScreen(screen); return;
                }
                client.setScreen(null); client.getLanguageManager().setSelected("es_es");
                reload = client.reloadResourcePacks(); phase = 3; return;
            }
            if (phase == 3 && reload.isDone()) {
                reload.get();
                details(client);
                REPORT.append("Resource reload in es_es completed; Aether artifact ships English only, so Aether fallback remains English while vanilla/shared translations change; repeated immutable captures and native rich factory still valid\n");
                complete(client);
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(OUTPUT + "-partial-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop();
        }
    }

    private static String seed(ServerPlayer owner) throws Exception {
        clear(owner); owner.setNoGravity(true); owner.teleportTo(0, 90, 0);
        serverSamples = new ArrayList<>();
        if (ABSENT) { serverSamples.addAll(paidPair(owner, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS))); }
        else {
            var api = new AetherIICompatibilityTest.NativeApi();
            for (String family : AetherTooltipSpike.FAMILIES) { serverSamples.add(item(family + "_boots")); }
            for (int i = 0; i < 7; i++) { serverSamples.addAll(paidPair(owner, serverSamples.get(i), new ItemStack(Items.DIAMOND_BOOTS))); }
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
        }
        var container = new SimpleContainer(54);
        for (int i = 0; i < serverSamples.size(); i++) { container.setItem(i, serverSamples.get(i)); }
        owner.openMenu(new SimpleMenuProvider((id, inventory, player) -> ChestMenu.sixRows(id, inventory, container), Component.literal("Aether tooltip spike")));
        return prepare(owner);
    }

    private static String prepare(ServerPlayer owner) throws Exception {
        clear(owner);
        if (!ABSENT) {
            var api = new AetherIICompatibilityTest.NativeApi(); api.accessories(owner).clearContent();
            String family = scene < 21 ? AetherTooltipSpike.FAMILIES.get(scene / 3)
                    : scene == 22 || scene == 23 ? "zanite" : "burrukai_plate";
            int index = scene < 21 ? (scene % 3 == 0 ? scene / 3 : 7 + (scene / 3) * 2 + scene % 3 - 1)
                    : scene == 21 ? 21 : scene < 24 ? scene : 9;
            owner.setItemSlot(EquipmentSlot.FEET, serverSamples.get(index).copy());
            if (!family.equals("sentry")) {
                owner.setItemSlot(EquipmentSlot.HEAD, item(family + "_helmet"));
                if (scene != 24) { owner.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate")); }
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
            if (scene == 24) { owner.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet")); } // Two pieces, set inactive.
        }
        var result = new JsonObject(); var roots = new JsonArray();
        var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var sample : serverSamples) { roots.add(ItemStack.CODEC.encodeStart(ops, sample).getOrThrow()); }
        result.add("samples", roots);
        if (!ABSENT) { result.add("feet", ItemStack.CODEC.encodeStart(ops, owner.getItemBySlot(EquipmentSlot.FEET)).getOrThrow()); }
        owner.inventoryMenu.broadcastChanges(); // The open chest does not include armor slots; use ordinary slot packets for the wearer's inventory menu.
        return result.toString();
    }

    private static boolean received(Minecraft client) {
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < expected.getAsJsonArray("samples").size(); i++) {
            var root = ItemStack.CODEC.parse(ops, expected.getAsJsonArray("samples").get(i)).getOrThrow();
            if (!ItemStack.matches(root, sample(client, i))) {
                if (ticks % 100 == 0) {
                    NetworkAcceptanceFiles.write(OUTPUT + "-mismatch.txt", "slot=" + i + "\nexpected=" + AetherTooltipSpike.encoded(client, root)
                            + "\nreceived=" + AetherTooltipSpike.encoded(client, sample(client, i)));
                }
                return false;
            }
        }
        return ABSENT || ItemStack.matches(ItemStack.CODEC.parse(ops, expected.get("feet")).getOrThrow(), client.player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static void configure(Minecraft client) {
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        job = server.submit(() -> {
            try { return prepare(server.getPlayerList().getPlayer(id)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private static void runScene(Minecraft client) throws Exception {
        var worn = client.player.getItemBySlot(EquipmentSlot.FEET);
        require(client.player.inventoryMenu.getSlot(8).getItem() == worn, "Worn inventory slot supplies a copy");
        var baseline = capture(client, worn, TooltipFlag.NORMAL, false, false);
        var proposal = capture(client, worn, TooltipFlag.NORMAL, false, true);
        var copyBaseline = capture(client, worn.copy(), TooltipFlag.NORMAL, false, false);
        var copyProposal = capture(client, worn.copy(), TooltipFlag.NORMAL, false, true);
        if (ModContent.isMixedBoots(worn)) {
            require(copyProposal.liveAttributes() == 0, "Identical copy borrowed worn effects");
            require(baseline.nativeAttributes() == copyBaseline.nativeAttributes(), "Native Item-type baseline unexpectedly distinguishes identical copy");
            require(proposal.nativeAttributes() == 0, "Candidate leaked native extra lines");
            if (scene < 18 && scene % 3 != 0 && scene / 3 != 0) {
                require(proposal.liveAttributes() > 0, "Candidate failed to display an active native set, scene=" + scene);
            }
            if (proposal.liveAttributes() > 0) {
                var oldDisplay = worn.get(DataComponents.TOOLTIP_DISPLAY);
                try {
                    for (var hidden : List.of(component("armor_set"), DataComponents.ATTRIBUTE_MODIFIERS)) {
                        worn.set(DataComponents.TOOLTIP_DISPLAY, AetherTooltipSpike.display(worn).withHidden(hidden, true));
                        require(capture(client, worn, TooltipFlag.NORMAL, false, true).liveAttributes() == 0,
                                "Real worn target ignored hidden native set/numeric details");
                        if (oldDisplay == null) { worn.remove(DataComponents.TOOLTIP_DISPLAY); }
                        else { worn.set(DataComponents.TOOLTIP_DISPLAY, oldDisplay); }
                    }
                } finally {
                    if (oldDisplay == null) { worn.remove(DataComponents.TOOLTIP_DISPLAY); }
                    else { worn.set(DataComponents.TOOLTIP_DISPLAY, oldDisplay); }
                }
            }
        }
        if (scene == 21) { require(proposal.liveAttributes() == 2, "Duplicate Burrukai set was lost/doubled"); }
        if (scene == 22 || scene == 23) { require(proposal.liveAttributes() == 5, "Concurrent Zanite/Neptune sets/gloves not reflected once each"); }
        if (scene == 22) { dualRoot = worn.copy(); dualActive = capture(client, worn, TooltipFlag.ADVANCED, false, true); }
        if (scene == 24) { require(proposal.liveAttributes() == 0, "Removed third piece retained active values"); }
        for (int i = 7; i < 21; i++) {
            var idle = sample(client, i);
            require(idle != worn, "Chest slot aliased worn equipment");
            var old = capture(client, idle, TooltipFlag.NORMAL, false, false);
            var candidate = capture(client, idle, TooltipFlag.NORMAL, false, true);
            require(candidate.liveAttributes() == 0 && candidate.nativeAttributes() == 0, "Chest preview borrowed equipped values");
            if (old.nativeAttributes() > 0) { borrowed++; REPORT.append("BORROW scene=").append(scene).append(" chest=").append(i).append(" nativeLines=").append(old.nativeAttributes()).append(" candidate=0\n"); }
        }
        REPORT.append("SCENE ").append(scene).append(" item=").append(BuiltInRegistries.ITEM.getKey(worn.getItem()))
                .append(" baselineCalls=").append(baseline.calls()).append(" baselineExtra=").append(baseline.nativeAttributes())
                .append(" identicalCopyExtra=").append(copyBaseline.nativeAttributes())
                .append(" candidateLive=").append(proposal.liveAttributes()).append(" copyLive=").append(copyProposal.liveAttributes()).append('\n');
    }

    private static void details(Minecraft client) throws Exception {
        for (int i = 0; i < 30; i++) {
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var compact = capture(client, sample(client, i), flag, false, true);
                var expanded = capture(client, sample(client, i), flag, true, true);
                if (i < 26 || i > 27) { require(compact.lines().equals(expanded.lines()), "Unexpected Aether/SG Shift-only presentation"); }
                ItemStack target = sample(client, i);
                var components = AetherTooltipSpike.gathering(compact, () -> ClientHooks.gatherTooltipComponents(target, compact.lines(), 20,
                        client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.font));
                long rich = components.stream().filter(value -> value.getClass().getName().endsWith("ClientCharmTooltip")).count();
                require(rich == compact.images().size() + (!ModContent.isMixedBoots(sample(client, i)) && sample(client, i).has(component("charms")) ? 1 : 0),
                        "GatherComponents lost/misplaced source markers at sample=" + i + " rich=" + rich);
                if (i == 24) { require(compact.images().size() == 2, "Installed source grids not separate"); }
                if (i == 24) {
                    require(compact.lines().stream().filter(line -> translated(line, "aether_ii.tooltip.item.reinforcement")).count() == 2,
                            "Source tiers missing or misleading root tier remains");
                    int left = index(compact.lines(), "tooltip.mismatchedboots.mixed_boots.left");
                    int right = index(compact.lines(), "tooltip.mismatchedboots.mixed_boots.right");
                    int first = 0, second = 0;
                    for (int row = 0; row < compact.lines().size(); row++) {
                        if (compact.images().containsKey(compact.lines().get(row))) {
                            if (row > left && row < right) { first++; }
                            else if (row > right) { second++; }
                        }
                    }
                    require(first == 1 && second == 1, "Installed charm grids lost source-local association");
                }
                if (i >= 26) {
                    String partner = i < 28 ? "justdirethings." : "item.silentgear.";
                    var baseline = capture(client, target, flag, false, false);
                    var expectedPartner = baseline.lines().stream().filter(line -> translated(line, partner)).toList();
                    require(!expectedPartner.isEmpty() && expectedPartner.equals(compact.lines().stream().filter(line -> translated(line, partner)).toList()),
                            "Existing JDT/SG source/root sections changed");
                }
            }
        }
        var root = sample(client, 24); var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (String hidden : List.of("charms", "reinforcement_tier", "all")) {
            var left = data.left().source(); var right = data.right().source();
            for (var source : List.of(left, right)) {
                source.set(DataComponents.TOOLTIP_DISPLAY, hidden.equals("all") ? new TooltipDisplay(true, new java.util.LinkedHashSet<>())
                        : TooltipDisplay.DEFAULT.withHidden(component(hidden), true));
            }
            var modified = root.copy(); modified.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(left), new BootHalf(right)));
            var captured = capture(client, modified, TooltipFlag.ADVANCED, true, true);
            if (!hidden.equals("reinforcement_tier")) { require(captured.images().isEmpty(), "Hidden original charms still published"); }
        }
        var hidden = root.copy(); hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        require(capture(client, hidden, TooltipFlag.NORMAL, false, false).lines().equals(capture(client, hidden, TooltipFlag.NORMAL, false, true).lines()), "Hidden root changed");
        AetherTooltipSpike.factoryMissing = true;
        try { require(capture(client, root, TooltipFlag.NORMAL, false, true).images().isEmpty(), "Names fallback published graphical markers"); }
        finally { AetherTooltipSpike.factoryMissing = false; }
        var ordinary = capture(client, root, TooltipFlag.NORMAL, false, true);
        String frozen = AetherTooltipSpike.encoded(client, root);
        for (var image : ordinary.images().values()) {
            var holders = (List<?>) image.getClass().getMethod("items").invoke(image);
            var charm = (ItemStack) holders.getFirst().getClass().getMethod("getStack").invoke(holders.getFirst());
            charm.set(DataComponents.CUSTOM_NAME, Component.literal("Disposable renderer payload"));
        }
        require(frozen.equals(AetherTooltipSpike.encoded(client, root)), "Renderer payload aliases frozen native holders");
        // Custom data may exceed the shipped two-holder boots. Never invoke the native broken >4 height route.
        var customSource = data.left().source();
        Object charms = customSource.get(component("charms"));
        var originalHolders = (List<?>) charms.getClass().getMethod("charmHolders").invoke(charms);
        customSource.set(component("charms"), charms.getClass().getConstructor(List.class)
                .newInstance(new ArrayList<>(java.util.Collections.nCopies(5, originalHolders.getFirst()))));
        var customRoot = root.copy();
        customRoot.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(customSource), data.right()));
        require(capture(client, customRoot, TooltipFlag.NORMAL, false, true).images().size() == 1, "Custom >4 source did not fall back locally to names");
        AetherTooltipSpike.formatterFault = true;
        try {
            var failed = capture(client, root, TooltipFlag.NORMAL, false, true);
            require(failed.lines().size() < ordinary.lines().size() && failed.images().size() == ordinary.images().size(), "Fault published partial root block/removed source state");
        } finally { AetherTooltipSpike.formatterFault = false; }
        var api = new AetherIICompatibilityTest.NativeApi();
        @SuppressWarnings("unchecked") var set = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) item("burrukai_plate_boots").get(component("armor_set"));
        require(api.count(client.player, set) == 2, "Presentation fault revoked independent accepted source recognition");
        REPORT.append("DETAILS normal/Shift/advanced/Shift+advanced: 30 native/mixed samples; source visibility, root hide-all/set/attributes; native factories and two installed-only grids; codec-deep renderer payload isolation; names fallback; scratch fault isolated\n");
    }

    private static AetherTooltipSpike.Capture capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean shift, boolean candidate) throws Exception {
        var captured = AetherTooltipSpike.capture(client, stack, flag, shift, candidate); captureCount++;
        var json = new JsonObject(); json.addProperty("scene", scene); json.addProperty("candidate", candidate);
        json.addProperty("advanced", flag.isAdvanced()); json.addProperty("shift", shift);
        json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("wornReference", stack == client.player.getItemBySlot(EquipmentSlot.FEET));
        json.addProperty("nativeAttributeCallbacks", captured.calls()); json.addProperty("nativeAdded", captured.nativeAttributes());
        json.addProperty("candidateActive", captured.liveAttributes()); json.addProperty("sourceImages", captured.images().size());
        var lines = new JsonArray();
        for (var line : captured.lines()) { lines.add(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, line).getOrThrow()); }
        json.add("lines", lines); CAPTURES.add(json); return captured;
    }

    private static ItemStack sample(Minecraft client, int index) { return client.player.containerMenu.getSlot(index).getItem(); }

    private static boolean translated(Component line, String prefix) {
        if (line.getContents() instanceof TranslatableContents text && text.getKey().startsWith(prefix)) { return true; }
        return line.getSiblings().stream().anyMatch(child -> translated(child, prefix));
    }
    private static int index(List<Component> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) { if (translated(lines.get(i), prefix)) { return i; } }
        return -1;
    }

    private static boolean image(Minecraft client) {
        if (screenshotFailure != null) { throw new AssertionError("Screenshot failed", screenshotFailure); }
        if (screenshotDone) { screenshotDone = false; return true; }
        if (screenshotPending || screen.frames < 5) { return false; }
        screenshotPending = true;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "tooltip-" + screenshots + ".png"));
                REPORT.append("FRAMEBUFFER ").append(image.getWidth()).append('x').append(image.getHeight()).append(" guiScale=")
                        .append(client.getWindow().getGuiScale()).append(" candidate=").append(screen.candidate)
                        .append(" frozenEquippedCapture=").append(screen.frozen != null).append('\n');
                screenshotDone = true;
            } catch (Throwable failure) { screenshotFailure = failure; }
            finally { screenshotPending = false; }
        }); return false;
    }

    private static final class Scene extends Screen {
        private final ItemStack root;
        private final boolean candidate;
        private AetherTooltipSpike.Capture frozen;
        private int frames;
        private Scene(ItemStack root, boolean candidate) { super(Component.literal("Aether tooltip spike")); this.root = root; this.candidate = candidate; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { graphics.fill(0, 0, width, height, 0xFF202020); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.text(font, frozen != null ? "Frozen equipped Zanite/Neptune candidate" : candidate
                    ? "Test-only Aether candidate" : "Unchanged production baseline", 20, 12, 0xFFFFFFFF, false);
            try {
                var captured = frozen != null ? frozen : capture(Minecraft.getInstance(), root, TooltipFlag.ADVANCED, false, candidate);
                AetherTooltipSpike.gathering(captured, () -> {
                    graphics.setTooltipForNextFrame(font, captured.lines(), Optional.empty(), root, 20, 40); return null;
                });
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
    }

    private static void complete(Minecraft client) {
        if (!ABSENT) { require(borrowed > 0, "Spike did not exercise the native cross-pair false positive"); }
        REPORT.append("captures=").append(captureCount).append(" absent=").append(ABSENT)
                .append("; every capture compared complete serialized root/history; no production source changes\n");
        NetworkAcceptanceFiles.write(OUTPUT + "-captures.json", CAPTURES.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n"); finished = true; client.stop();
    }
}
