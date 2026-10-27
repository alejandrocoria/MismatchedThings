package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.client.compatibility.aquaculture.AquacultureTooltipClient;
import games.alejandrocoria.mismatchedboots.compatibility.aquaculture.AquacultureTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedboots.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AquacultureTooltipClientTest {
    private static final String TITLE = "aquaculture.neptunium_boots.tooltip.title";
    private static final String DESCRIPTION = "aquaculture.neptunium_boots.tooltip.desc";
    private static final TagKey<Item> TOOLTIP = TagKey.create(Registries.ITEM, Identifier.parse("aquaculture:tooltip"));
    private static final Component FINAL_LINE = Component.literal("aquaculture fixture final root hook");
    private static final Component PARTIAL_LINE = Component.literal("must not escape failed native invocation");
    private static int heldKey = -1;
    private static int ticks;
    private static int finalEvents;
    private static int failures;
    private static boolean finished;

    private AquacultureTooltipClientTest() {
    }

    public static Boolean key(int key) {
        return heldKey < 0 || key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT ? null : key == heldKey;
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        String mode = System.getProperty("mismatchedboots.runtimetest.aquacultureTooltip");
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client is visible");
            require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Automated client is not muted");
            require(++ticks < 3_600, "Aquaculture tooltip startup timed out");
            if (ticks < 30 || client.level == null) {
                return;
            }
            if (mode.equals("absent")) {
                require(!ModList.get().isLoaded("aquaculture"), "Absent profile loaded Aquaculture");
                var lines = new ArrayList<Component>();
                AquacultureTooltipCompatibility.appendSource(new ItemStack(Items.DIAMOND_BOOTS), TooltipFlag.NORMAL, lines::add);
                var root = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                require(lines.isEmpty() && capture(root, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipClientTest::nativeLine),
                        "Absent mod contributed a description");
                NetworkAcceptanceFiles.write("absent-report.txt", "Aquaculture: absent-mod packaged client passed\n");
            } else {
                NeoForge.EVENT_BUS.addListener(AquacultureTooltipClientTest::finalTooltip);
                checkNativeSources();
                NetworkAcceptanceFiles.write("client-report.txt", "Aquaculture: native text/styles and left/right Shift; "
                        + "both orientations; duplicate sources; "
                        + "F3+H/name/hidden boundaries; live tag removal/restoration; root hooks/attributes/history; atomic failure passed\n");
            }
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(mode, failure);
            finished = true;
            client.stop();
        } finally {
            heldKey = -1;
        }
    }

    private static void finalTooltip(ItemTooltipEvent event) {
        finalEvents++;
        event.getToolTip().add(FINAL_LINE);
    }

    private static void checkNativeSources() throws Exception {
        var boots = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("aquaculture:neptunium_boots")).orElseThrow());
        require(boots.is(TOOLTIP), "Native tooltip tag was not loaded");
        var nativeMethod = Class.forName("com.teammetallurgy.aquaculture.misc.AquaTooltip").getMethod("onTooltip", ItemTooltipEvent.class);
        var pair = MixedBootsFactory.createPair(boots, new ItemStack(Items.DIAMOND_BOOTS));
        for (int pressed : new int[]{0, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT}) {
            heldKey = pressed;
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var expected = new ArrayList<Component>();
                nativeMethod.invoke(null, new ItemTooltipEvent(boots.copy(), null, expected, flag, Item.TooltipContext.EMPTY));
                String expectedKey = pressed == GLFW.GLFW_KEY_LEFT_SHIFT ? DESCRIPTION : TITLE;
                require(expected.size() == 1 && translation(expected.getFirst(), expectedKey), "Native input control failed");
                checkRoot(pair.primary(), flag, expected.getFirst(), true, false);
                checkRoot(pair.inverse(), flag, expected.getFirst(), false, true);
                var same = MixedBootsFactory.createPair(boots, boots).primary();
                checkRoot(same, flag, expected.getFirst(), true, true);
            }
        }
        heldKey = 0;
        var named = boots.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named Neptunium"));
        var namedRoot = MixedBootsFactory.createPair(named, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(capture(namedRoot, TooltipFlag.ADVANCED).stream().anyMatch(line -> line.getString().contains("Named Neptunium")),
                "Custom source name disappeared");
        var hidden = boots.copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        var hiddenRoot = MixedBootsFactory.createPair(hidden, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(capture(hiddenRoot, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipClientTest::nativeLine),
                "Hidden source exposed native details");
        var hiddenWhole = pair.primary().copy();
        hiddenWhole.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        require(capture(hiddenWhole, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipClientTest::nativeLine),
                "Hidden root exposed source details");
        checkTagChanges(boots, pair.primary());

        // The handler needs a window, not a player or level in the event.
        var emptyContextLines = new ArrayList<Component>();
        AquacultureTooltipCompatibility.appendSource(boots, TooltipFlag.NORMAL, emptyContextLines::add);
        require(emptyContextLines.size() == 1, "Empty event context/null player formatting failed");
        checkAtomicFailure(boots, pair.primary());
    }

    private static void checkRoot(ItemStack root, TooltipFlag flag, Component expected, boolean left, boolean right) {
        var before = root.copy();
        finalEvents = 0;
        var lines = capture(root, flag);
        require(finalEvents == 1 && lines.getLast().equals(FINAL_LINE), "Source formatting replayed or displaced final root hooks");
        int leftIndex = index(lines, "tooltip.mismatchedboots.mixed_boots.left");
        int rightIndex = index(lines, "tooltip.mismatchedboots.mixed_boots.right");
        int firstSeparator = lines.indexOf(TooltipSeparator.INSTANCE);
        int lastSeparator = lines.lastIndexOf(TooltipSeparator.INSTANCE);
        require(leftIndex >= 0 && firstSeparator > leftIndex && rightIndex > firstSeparator && lastSeparator > rightIndex,
                "Source sections/separators missing");
        var leftLines = lines.subList(leftIndex + 1, firstSeparator);
        var rightLines = lines.subList(rightIndex + 1, lastSeparator);
        require(leftLines.stream().filter(AquacultureTooltipClientTest::nativeLine).toList().equals(left ? List.of(expected) : List.of()),
                "Left source native text/style mismatch");
        require(rightLines.stream().filter(AquacultureTooltipClientTest::nativeLine).toList().equals(right ? List.of(expected) : List.of()),
                "Right source native text/style mismatch");
        require(lines.subList(lastSeparator + 1, lines.size()).stream().noneMatch(AquacultureTooltipClientTest::nativeLine),
                "Native description leaked into root");
        require(ItemStack.matches(before, root), "Tooltip changed root attributes or source snapshots");
        require(capture(root, flag).equals(lines), "Repeated capture accumulated or changed source lines");
    }

    private static void checkTagChanges(ItemStack boots, ItemStack root) throws Exception {
        var holder = boots.typeHolder();
        var original = holder.tags().toList();
        Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bindTags.setAccessible(true);
        try {
            bindTags.invoke(holder, original.stream().filter(tag -> !tag.equals(TOOLTIP)).toList());
            require(capture(root, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipClientTest::nativeLine), "Removed tag was cached");
        } finally {
            bindTags.invoke(holder, original);
        }
        require(capture(root, TooltipFlag.NORMAL).stream().anyMatch(AquacultureTooltipClientTest::nativeLine), "Restored tag was ignored");
        var unrelated = new ItemStack(Items.DIAMOND_BOOTS).typeHolder();
        var unrelatedTags = unrelated.tags().toList();
        try {
            var tagged = new ArrayList<>(unrelatedTags);
            tagged.add(TOOLTIP);
            bindTags.invoke(unrelated, tagged);
            var lines = new ArrayList<Component>();
            AquacultureTooltipCompatibility.appendSource(new ItemStack(Items.DIAMOND_BOOTS), TooltipFlag.NORMAL, lines::add);
            require(lines.isEmpty(), "Adapter admitted an unrelated tagged item");
        } finally {
            bindTags.invoke(unrelated, unrelatedTags);
        }
    }

    private static void checkAtomicFailure(ItemStack boots, ItemStack root) throws Exception {
        var binding = AquacultureTooltipClient.class.getDeclaredField("nativeTooltip");
        binding.setAccessible(true);
        binding.set(null, AquacultureTooltipClientTest.class.getMethod("failAfterLine", ItemTooltipEvent.class));
        var before = boots.copy();
        var lines = new ArrayList<Component>();
        AquacultureTooltipCompatibility.appendSource(boots, TooltipFlag.NORMAL, lines::add);
        AquacultureTooltipCompatibility.appendSource(boots, TooltipFlag.NORMAL, lines::add);
        require(lines.isEmpty() && failures == 1 && ItemStack.matches(before, boots), "Native failure leaked partial text or modified source");
        var degraded = capture(root, TooltipFlag.NORMAL);
        require(!degraded.contains(PARTIAL_LINE) && index(degraded, "tooltip.mismatchedboots.mixed_boots.left") >= 0
                        && degraded.getLast().equals(FINAL_LINE), "Local failure damaged normal tooltip sections");
    }

    public static void failAfterLine(ItemTooltipEvent event) {
        failures++;
        event.getToolTip().add(PARTIAL_LINE);
        event.getItemStack().set(DataComponents.CUSTOM_NAME, PARTIAL_LINE);
        throw new IllegalStateException("intentional Aquaculture presentation fault");
    }

    private static List<Component> capture(ItemStack root, TooltipFlag flag) {
        return root.getTooltipLines(Item.TooltipContext.EMPTY, null, flag);
    }

    private static boolean nativeLine(Component line) {
        return translation(line, TITLE) || translation(line, DESCRIPTION);
    }

    private static int index(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (translation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean translation(Component line, String key) {
        return line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(sibling -> translation(sibling, key));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
