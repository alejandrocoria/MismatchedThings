package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.client.compatibility.aquaculture.AquacultureTooltipClient;
import games.alejandrocoria.mismatchedleggings.compatibility.aquaculture.AquacultureTooltipCompatibility;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class AquacultureTooltipChecks {
    private static final String TITLE = "aquaculture.neptunium_leggings.tooltip.title";
    private static final String DESCRIPTION = "aquaculture.neptunium_leggings.tooltip.desc";
    private static final TagKey<Item> TOOLTIP = TagKey.create(Registries.ITEM, Identifier.parse("aquaculture:tooltip"));
    private static final Component FINAL_LINE = Component.literal("aquaculture fixture final root hook");
    private static final Component PARTIAL_LINE = Component.literal("must not escape failed native invocation");
    private static final Component SOURCE_LORE = Component.literal("aquaculture fixture source lore");
    private static int heldKey = -1;
    private static int finalEvents;
    private static int failures;

    private AquacultureTooltipChecks() {
    }

    public static Boolean key(int key) {
        return heldKey < 0 || key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT ? null : key == heldKey;
    }

    static void verify() throws Exception {
        NeoForge.EVENT_BUS.addListener(AquacultureTooltipChecks::finalTooltip);
        try {
            checkNativeSources();
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "aquaculture-tooltip.txt"),
                    "Native text/styles and left/right Shift; both orientations and duplicate sources;\n"
                    + "advanced ID placement; name/hidden/null-context boundaries; native tag reload application;\n"
                    + "one final root event; immutable history; atomic once-disabled failure; optional Boots independence passed\n");
        } finally {
            heldKey = -1;
        }
    }

    private static void finalTooltip(ItemTooltipEvent event) {
        finalEvents++;
        event.getToolTip().add(FINAL_LINE);
    }

    private static void checkNativeSources() throws Exception {
        var leggings = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("aquaculture:neptunium_leggings")).orElseThrow());
        require(leggings.is(TOOLTIP), "Native tooltip tag was not loaded");
        var nativeMethod = Class.forName("com.teammetallurgy.aquaculture.misc.AquaTooltip").getMethod("onTooltip", ItemTooltipEvent.class);
        var pair = MixedLeggingsFactory.createPair(leggings, new ItemStack(Items.DIAMOND_LEGGINGS));
        for (int pressed : new int[]{0, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT}) {
            heldKey = pressed;
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var expected = new ArrayList<Component>();
                nativeMethod.invoke(null, new ItemTooltipEvent(leggings.copy(), null, expected, flag, Item.TooltipContext.EMPTY));
                String expectedKey = pressed == GLFW.GLFW_KEY_LEFT_SHIFT ? DESCRIPTION : TITLE;
                require(expected.size() == 1 && translation(expected.getFirst(), expectedKey), "Native input control failed");
                checkRoot(pair.primary(), flag, expected.getFirst(), true, false);
                checkRoot(pair.inverse(), flag, expected.getFirst(), false, true);
                var same = MixedLeggingsFactory.createPair(leggings, leggings).primary();
                checkRoot(same, flag, expected.getFirst(), true, true);
            }
        }
        heldKey = 0;
        var named = leggings.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named Neptunium"));
        named.set(DataComponents.LORE, new ItemLore(List.of(SOURCE_LORE)));
        var namedRoot = MixedLeggingsFactory.createPair(named, new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        var namedLines = capture(namedRoot, TooltipFlag.ADVANCED);
        require(namedLines.stream().anyMatch(line -> line.getString().contains("Named Neptunium"))
                        && namedLines.stream().anyMatch(line -> line.getString().equals(SOURCE_LORE.getString())),
                "Native contribution displaced a custom source name or lore");
        var hidden = leggings.copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        var hiddenRoot = MixedLeggingsFactory.createPair(hidden, new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        require(capture(hiddenRoot, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipChecks::nativeLine),
                "Hidden source exposed native details");
        var hiddenWhole = pair.primary().copy();
        hiddenWhole.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        require(capture(hiddenWhole, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipChecks::nativeLine),
                "Hidden root exposed source details");
        checkTagChanges(leggings, pair.primary());

        // The handler needs a window, not a player or level in the event.
        var emptyContextLines = new ArrayList<Component>();
        AquacultureTooltipCompatibility.appendSource(leggings, TooltipFlag.NORMAL, emptyContextLines::add);
        require(emptyContextLines.size() == 1, "Empty event context/null player formatting failed");
        checkBootsIndependence();
        checkAtomicFailure(leggings, pair.primary());
        checkBootsIndependence();
    }

    private static void checkRoot(ItemStack root, TooltipFlag flag, Component expected, boolean left, boolean right) {
        var before = root.copy();
        finalEvents = 0;
        var lines = capture(root, flag);
        require(finalEvents == 1 && lines.getLast().equals(FINAL_LINE), "Source formatting replayed or displaced final root hooks");
        int leftIndex = index(lines, "tooltip.mismatchedleggings.mixed_leggings.left");
        int rightIndex = index(lines, "tooltip.mismatchedleggings.mixed_leggings.right");
        int firstSeparator = lines.indexOf(TooltipSeparator.INSTANCE);
        int lastSeparator = lines.lastIndexOf(TooltipSeparator.INSTANCE);
        require(leftIndex >= 0 && firstSeparator > leftIndex && rightIndex > firstSeparator && lastSeparator > rightIndex,
                "Source sections/separators missing");
        var leftLines = lines.subList(leftIndex + 1, firstSeparator);
        var rightLines = lines.subList(rightIndex + 1, lastSeparator);
        if (flag.isAdvanced()) {
            var nativeId = Component.literal("aquaculture:neptunium_leggings").withStyle(ChatFormatting.DARK_GRAY);
            require((!left || leftLines.getFirst().equals(nativeId)) && (!right || rightLines.getFirst().equals(nativeId)),
                    "Advanced source ID must precede the native contribution in dark gray");
        }
        require(leftLines.stream().filter(AquacultureTooltipChecks::nativeLine).toList().equals(left ? List.of(expected) : List.of()),
                "Left source native text/style mismatch");
        require(rightLines.stream().filter(AquacultureTooltipChecks::nativeLine).toList().equals(right ? List.of(expected) : List.of()),
                "Right source native text/style mismatch");
        require(lines.subList(lastSeparator + 1, lines.size()).stream().noneMatch(AquacultureTooltipChecks::nativeLine),
                "Native description leaked into root");
        require(ItemStack.matches(before, root), "Tooltip changed root attributes or source snapshots");
        require(capture(root, flag).equals(lines), "Repeated capture accumulated or changed source lines");
    }

    private static void checkTagChanges(ItemStack leggings, ItemStack root) {
        var original = new LinkedHashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> original.put(tag.key(), tag.stream().toList()));
        try {
            var changed = new LinkedHashMap<>(original);
            changed.put(TOOLTIP, original.get(TOOLTIP).stream()
                    .filter(holder -> !holder.equals(leggings.typeHolder())).toList());
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
            require(capture(root, TooltipFlag.NORMAL).stream().noneMatch(AquacultureTooltipChecks::nativeLine), "Removed tag was cached");
            var contribution = new ArrayList<Component>();
            Class<?> handler = Class.forName("com.teammetallurgy.aquaculture.misc.AquaTooltip");
            handler.getMethod("onTooltip", ItemTooltipEvent.class).invoke(null,
                    new ItemTooltipEvent(leggings.copy(), null, contribution, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY));
            require(contribution.isEmpty(), "Native tag removal control still contributed a description");

            var members = new ArrayList<>(changed.get(TOOLTIP));
            members.add(new ItemStack(Items.DIAMOND_LEGGINGS).typeHolder());
            changed.put(TOOLTIP, members);
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
            var unrelatedLines = new ArrayList<Component>();
            AquacultureTooltipCompatibility.appendSource(new ItemStack(Items.DIAMOND_LEGGINGS), TooltipFlag.NORMAL, unrelatedLines::add);
            require(unrelatedLines.isEmpty(), "Adapter admitted an unrelated tagged item");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot invoke native tag control", failure);
        } finally {
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, original)).apply();
        }
        require(capture(root, TooltipFlag.NORMAL).stream().anyMatch(AquacultureTooltipChecks::nativeLine), "Restored tag was ignored");
    }

    private static void checkBootsIndependence() {
        if (!Boolean.getBoolean("leggings.test.boots")) {
            return;
        }
        var nativeBoots = AquacultureChecks.nativeItem("neptunium_boots");
        var roots = BootsCoexistence.pair(nativeBoots, new ItemStack(Items.IRON_BOOTS));
        for (var root : roots) {
            var lines = capture(root, TooltipFlag.NORMAL);
            require(lines.stream().filter(line -> translation(line, "aquaculture.neptunium_boots.tooltip.title")).count() == 1
                            && lines.stream().noneMatch(AquacultureTooltipChecks::nativeLine),
                    "Leggings tooltip handling changed the independent Boots contribution");
        }
    }

    private static void checkAtomicFailure(ItemStack leggings, ItemStack root) throws Exception {
        var binding = AquacultureTooltipClient.class.getDeclaredField("nativeTooltip");
        binding.setAccessible(true);
        binding.set(null, AquacultureTooltipChecks.class.getMethod("failAfterLine", ItemTooltipEvent.class));
        var before = leggings.copy();
        var lines = new ArrayList<Component>();
        AquacultureTooltipCompatibility.appendSource(leggings, TooltipFlag.NORMAL, lines::add);
        AquacultureTooltipCompatibility.appendSource(leggings, TooltipFlag.NORMAL, lines::add);
        require(lines.isEmpty() && failures == 1 && ItemStack.matches(before, leggings), "Native failure leaked partial text or modified source");
        var degraded = capture(root, TooltipFlag.NORMAL);
        require(!degraded.contains(PARTIAL_LINE) && index(degraded, "tooltip.mismatchedleggings.mixed_leggings.left") >= 0
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
