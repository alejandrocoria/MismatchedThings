package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Structured production comparisons, independent native oracle and capture-scoped window polling. */
public final class TooltipClient {
    static final String VARIANT = System.getProperty("leggings.test.sgTooltipVariant", "normal");
    private static int ticks;
    private static int events;
    private static int comparisons;
    private static int noOrdinaryLines;
    private static boolean capturing;
    private static boolean observing;
    private static boolean presentation = true;
    private static boolean leggingsCapture;
    static boolean faultActive;
    static int faultCalls;
    private static boolean finished;
    private static Set<Integer> pressed;
    private static KeyMapping descriptionMapping;

    private TooltipClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> {
            if (capturing) {
                events++;
            }
        });
    }

    public static Boolean key(int code) {
        return observing && pressed != null ? pressed.contains(code) : null;
    }

    public static boolean omitPresentation() {
        return observing && !presentation;
    }

    public static void bindingFault() throws ClassNotFoundException {
        if (faultActive && capturing && presentation && leggingsCapture && VARIANT.equals("binding")) {
            faultCalls++;
            throw new ClassNotFoundException("Controlled optional presentation binding failure");
        }
    }

    public static void formatterFault(List<Component> scratch) {
        if (faultActive && capturing && presentation && leggingsCapture && VARIANT.equals("formatter")) {
            require(!scratch.isEmpty(), "Formatter fault ran before native list population");
            faultCalls++;
            scratch.add(Component.literal("must not escape failed SG source formatting"));
            throw new IllegalStateException("Controlled post-population presentation failure");
        }
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3000, "Tooltip client timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Tooltip client must stay hidden/muted");
            require(!Files.exists(StateChecks.report("server.failure.txt")), "Tooltip server failed");
            if (client.level == null || client.player == null || client.getOverlay() != null
                    || !(client.player.containerMenu instanceof ChestMenu) || !Files.exists(StateChecks.report("tooltip-samples.json"))) {
                return;
            }
            require(client.getSingleplayerServer() == null, "Tooltip profile requires actual dedicated TCP packets");
            var expected = JsonParser.parseString(Files.readString(StateChecks.report("tooltip-samples.json"))).getAsJsonArray();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int i = 0; i < expected.size(); i++) {
                if (!ItemStack.matches(ItemStack.CODEC.parse(ops, expected.get(i)).getOrThrow(), client.player.containerMenu.getSlot(i).getItem())) {
                    return;
                }
            }
            run(client, expected.size());
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void run(Minecraft client, int count) throws Exception {
        if (VARIANT.equals("absent")) {
            absent(client, count);
            return;
        }
        var keys = Class.forName("net.silentchaos512.gear.client.KeyTracker");
        var properties = (KeyMapping) keys.getField("DISPLAY_PROPERTIES").get(null);
        var traits = (KeyMapping) keys.getField("DISPLAY_TRAITS").get(null);
        var construction = (KeyMapping) keys.getField("DISPLAY_CONSTRUCTION").get(null);
        var mappings = List.of(properties, traits, construction);
        var oldKeys = mappings.stream().map(KeyMapping::getKey).toList();
        boolean oldDescriptionDown = traits.isDown();
        var setting = Class.forName("net.silentchaos512.gear.Config$Client").getField("vanillaStyleTooltips").get(null);
        boolean oldStyle = (boolean) setting.getClass().getMethod("get").invoke(setting);
        var report = new StringBuilder("Production source appender; independent native oracle; paid roots received over TCP\n");
        try {
            properties.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_CONTROL));
            traits.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT));
            construction.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT));
            setting.getClass().getMethod("set", Object.class).invoke(setting, false);
            observing = true;
            descriptionMapping = traits;
            if (VARIANT.equals("binding") || VARIANT.equals("formatter")) {
                input(7, false);
                TooltipCases.fault(client, count, report);
                complete(report);
                return;
            }
            for (int mode : new int[]{0, 1, 3, 4, 7}) {
                input(mode, false);
                require((boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null) == ((mode & 1) != 0)
                        && (boolean) keys.getMethod("isDisplayTraitDescriptionsDown").invoke(null) == ((mode & 2) != 0)
                        && (boolean) keys.getMethod("isDisplayConstructionDown").invoke(null) == ((mode & 4) != 0),
                        "Native tracker did not read capture-scoped keys");
                if (mode == 0) {
                    TooltipNativeControl.requirePartSummary(MixedLeggingsStructure.validData(
                            client.player.containerMenu.getSlot(4).getItem()).orElseThrow().right().source());
                }
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    for (int i = 0; i < count; i++) {
                        compare(client, client.player.containerMenu.getSlot(i).getItem(), flag);
                    }
                }
                report.append("Native structured equality: key mode=").append(mode).append("; normal/advanced; ")
                        .append(count).append(" paid inverse roots\n");
            }
            TooltipCases.normal(client, count, mappings, setting, report);
            require(noOrdinaryLines > 0, "No source without ordinary lines was actually tested");
            complete(report);
        } finally {
            observing = false;
            TooltipObservation.active = false;
            pressed = null;
            presentation = true;
            faultActive = false;
            traits.setDown(oldDescriptionDown);
            descriptionMapping = null;
            for (int i = 0; i < mappings.size(); i++) {
                mappings.get(i).setKey(oldKeys.get(i));
            }
            setting.getClass().getMethod("set", Object.class).invoke(setting, oldStyle);
        }
    }

    private static void absent(Minecraft client, int count) throws Exception {
        observing = true;
        try {
            require(!net.neoforged.fml.ModList.get().isLoaded("silentgear"), "Absent client unexpectedly contains SG");
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                for (int i = 0; i < count; i++) {
                    compare(client, client.player.containerMenu.getSlot(i).getItem(), flag);
                }
            }
            var adapter = Class.forName("games.alejandrocoria.mismatchedleggings.client.compatibility.silentgear.SilentGearTooltipClient");
            var bindings = adapter.getDeclaredField("bindings");
            bindings.setAccessible(true);
            require(bindings.get(null) == null, "Absent native bindings were resolved");
            complete(new StringBuilder("Quiet native absence; unchanged ordinary/root presentation and unbound optional client API\n"));
        } finally {
            observing = false;
            TooltipObservation.active = false;
            presentation = true;
        }
    }

    private static void complete(StringBuilder report) throws Exception {
        require(comparisons > 0 && TooltipObservation.recalculations == 0, "Tooltip path recalculated native data: " + TooltipObservation.recalculations);
        report.append("Comparisons=").append(comparisons).append("; recalculations=").append(TooltipObservation.recalculations)
                .append("; source blocks without ordinary lines=").append(noOrdinaryLines)
                .append("; unchanged root/history snapshots, source IDs/separators/lore and root totals\n");
        Files.writeString(StateChecks.report("tooltip-client.txt"), report);
    }

    static void input(int mode, boolean rebound) {
        pressed = new HashSet<>();
        if ((mode & 1) != 0) { pressed.add(rebound ? GLFW.GLFW_KEY_P : GLFW.GLFW_KEY_LEFT_CONTROL); }
        if ((mode & 2) != 0) { pressed.add(rebound ? GLFW.GLFW_KEY_T : GLFW.GLFW_KEY_LEFT_SHIFT); }
        if ((mode & 4) != 0) { pressed.add(rebound ? GLFW.GLFW_KEY_C : GLFW.GLFW_KEY_LEFT_ALT); }
        // SG polls modifier keys directly but reads KeyMapping.isDown for a rebound description key.
        if (descriptionMapping != null) {
            descriptionMapping.setDown((mode & 2) != 0);
        }
    }

    static void compare(Minecraft client, ItemStack root, TooltipFlag flag) throws Exception {
        compare(client, root, flag, true, Set.of());
    }

    static void compare(Minecraft client, ItemStack root, TooltipFlag flag, boolean nativeControl, Set<Integer> omitted) throws Exception {
        boolean previous = TooltipObservation.active;
        TooltipObservation.active = true;
        try {
            compareObserved(client, root, flag, nativeControl, omitted);
        } finally {
            TooltipObservation.active = previous;
        }
    }

    private static void compareObserved(Minecraft client, ItemStack root, TooltipFlag flag, boolean nativeControl, Set<Integer> omitted) throws Exception {
        var before = root.copy();
        var expected = new ArrayList<>(capture(client, root, flag, false));
        var sources = TooltipFixtures.sources(root);
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            var snapshot = source.copy();
            var block = VARIANT.equals("absent") || omitted.contains(i) ? List.<Component>of() : TooltipNativeControl.details(client, source, flag);
            if (!block.isEmpty()) {
                if (nativeControl) {
                    var nativeLines = capture(client, source, flag, false);
                    require(nativeLines.size() > block.size() && nativeLines.subList(1, 1 + block.size()).equals(block),
                            "Independent source oracle differs from complete native tooltip");
                }
                int insertion = identity(expected, root, i == 0 ? "left" : "right") + 1 + (flag.isAdvanced() ? 1 : 0);
                if (expected.get(insertion).equals(TooltipSeparator.INSTANCE)) {
                    noOrdinaryLines++;
                }
                expected.addAll(insertion, block);
            }
            require(ItemStack.matches(snapshot, source), "Native tooltip oracle changed source cache");
        }
        var actual = capture(client, root, flag, true);
        require(expected.equals(actual), "Mixed source insertion/order/styles/root output differ from native expectation");
        require(ItemStack.matches(before, root), "Tooltip changed complete root/source/live state");
        if (comparisons == 0) {
            Files.writeString(StateChecks.report("tooltip-compact.txt"), String.join("\n", actual.stream().map(Component::getString).toList()));
        }
        comparisons++;
    }

    static List<Component> capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean production) {
        boolean previous = TooltipObservation.active;
        TooltipObservation.active = true;
        presentation = production;
        capturing = true;
        events = 0;
        leggingsCapture = MixedLeggingsStructure.isMixedItem(stack);
        try {
            var result = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
            boolean hidden = stack.getOrDefault(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                    net.minecraft.world.item.component.TooltipDisplay.DEFAULT).hideTooltip();
            require(events == (hidden ? 0 : 1), "Missing or recursively replayed final tooltip event: " + events);
            return result;
        } finally {
            capturing = false;
            leggingsCapture = false;
            TooltipObservation.active = previous;
        }
    }

    private static int identity(List<Component> lines, ItemStack root, String side) {
        String key = MixedLeggingsStructure.isMixedItem(root) ? "tooltip.mismatchedleggings.mixed_leggings." + side
                : "tooltip.mismatchedboots.mixed_boots." + side;
        for (int i = 0; i < lines.size(); i++) {
            if (translation(lines.get(i), key)) {
                return i;
            }
        }
        throw new AssertionError("Missing source identity: " + side);
    }

    static boolean translation(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(part -> translation(part, key));
    }
}
