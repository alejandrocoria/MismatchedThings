package games.alejandrocoria.mismatchedleggings.test;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.foundation.item.TooltipModifier;
import com.zurrtum.create.client.infrastructure.config.AllConfigs;
import games.alejandrocoria.mismatchedleggings.compatibility.createfly.CreateFlyMixinPlugin;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class CreateFlyTooltipChecks {
    public static Boolean shiftDown;
    private static final Component FOREIGN_HOOK = Component.literal("create-fly-final-hook");
    private static final Component SOURCE_LORE = Component.literal("create-fly-source-lore");
    private static int hookCalls;
    private static ItemStack hookStack;

    private CreateFlyTooltipChecks() {
    }

    static void verify(Minecraft client) {
        var plugin = new CreateFlyMixinPlugin();
        require(plugin.shouldApplyMixin("games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter",
                "games.alejandrocoria.mismatchedleggings.mixin.compatibility.createfly.CreateFlyMixedLeggingsTooltipFormatterMixin"),
                "Production tooltip guard rejected the pinned artifact");
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            hookCalls++;
            hookStack = stack;
            lines.add(FOREIGN_HOOK);
        });
        boolean previous = AllConfigs.client().tooltips.get();
        try {
            var source = AllItems.CARDBOARD_LEGGINGS.getDefaultInstance();
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
            checkSource(client, source, List.of(pair.primary(), pair.inverse()));
            var same = MixedLeggingsFactory.createPair(source, source);
            shiftDown = false;
            AllConfigs.client().tooltips.set(true);
            require(occurrences(tooltip(client, same.primary(), TooltipFlag.NORMAL), contribution(source, client)) == 2,
                    "Identical sources must each retain exactly one native description");

            if (Boolean.getBoolean("leggings.test.boots")) {
                for (Item item : List.of(AllItems.CARDBOARD_BOOTS, AllItems.COPPER_DIVING_BOOTS, AllItems.NETHERITE_DIVING_BOOTS)) {
                    var boots = item.getDefaultInstance();
                    checkSource(client, boots, BootsCoexistence.pair(boots, new ItemStack(Items.IRON_BOOTS)));
                }
            }
        } finally {
            AllConfigs.client().tooltips.set(previous);
            shiftDown = null;
        }
    }

    private static void checkSource(Minecraft client, ItemStack source, List<ItemStack> roots) {
        List<Component> normal = null;
        List<Component> expanded = null;
        for (boolean shifted : List.of(false, true)) {
            shiftDown = shifted;
            require(client.hasShiftDown() == shifted, "Native window-key Shift state was not selected");
            var expected = contribution(source, client);
            require(expected.stream().anyMatch(line -> !line.getString().isBlank()), "Native description was empty");
            if (shifted) {
                expanded = expected;
            } else {
                normal = expected;
            }

            for (boolean enabled : List.of(true, false, true)) {
                AllConfigs.client().tooltips.set(enabled);
                var nativeLines = tooltip(client, source, TooltipFlag.NORMAL);
                require(occurrences(nativeLines, expected) == (enabled ? 1 : 0), "Native config control disagreed");
                for (int index = 0; index < roots.size(); index++) {
                    var root = roots.get(index);
                    var before = root.copy();
                    var lines = tooltip(client, root, TooltipFlag.NORMAL);
                    require(occurrences(lines, expected) == (enabled ? 1 : 0), "Mixed description ignored config or duplicated lines");
                    var advanced = tooltip(client, root, TooltipFlag.ADVANCED);
                    require(occurrences(advanced, expected) == (enabled ? 1 : 0), "Advanced mode changed native description");
                    var id = Component.literal(BuiltInRegistries.ITEM.getKey(source.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY);
                    require(advanced.contains(id), "Advanced source ID disappeared");
                    require(lines.stream().filter(line -> line == TooltipSeparator.INSTANCE).count() == 2,
                            "Native source descriptions changed section separators");
                    if (enabled) {
                        int sourceName = sectionStart(lines, root, index == 0);
                        int advancedSourceName = sectionStart(advanced, root, index == 0);
                        require(lines.subList(sourceName + 1, sourceName + 1 + expected.size()).equals(expected),
                                "Native contribution left its corresponding source section");
                        require(advanced.get(advancedSourceName + 1).equals(id)
                                        && advanced.subList(advancedSourceName + 2, advancedSourceName + 2 + expected.size()).equals(expected),
                                "Advanced IDs no longer precede native source descriptions");
                    }
                    require(ItemStack.matches(before, root), "Tooltip changed root or source components");
                }
            }
        }
        require(!normal.equals(expanded), "Native Cardboard/Diving descriptions did not react to Shift");
        shiftDown = false;
        AllConfigs.client().tooltips.set(true);
        var expected = contribution(source, client);
        var player = client.player;
        try {
            client.player = null;
            require(occurrences(tooltip(client, roots.getFirst(), TooltipFlag.NORMAL), expected) == 0,
                    "Absent player still produced a native source description");
        } finally {
            client.player = player;
        }

        var hidden = source.copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        var decorated = source.copy();
        decorated.set(DataComponents.LORE, new ItemLore(List.of(SOURCE_LORE)));
        for (var root : mixedPair(hidden)) {
            require(occurrences(tooltip(client, root, TooltipFlag.NORMAL), expected) == 0, "Hidden source leaked native description");
        }
        for (var root : mixedPair(decorated)) {
            var enabledLines = tooltip(client, root, TooltipFlag.NORMAL);
            AllConfigs.client().tooltips.set(false);
            var disabledLines = tooltip(client, root, TooltipFlag.NORMAL);
            var preserved = new ArrayList<>(enabledLines);
            int start = subsequence(preserved, expected);
            require(start >= 0, "Enabled description missing from decorated source");
            preserved.subList(start, start + expected.size()).clear();
            require(preserved.equals(disabledLines), "Disabling Create descriptions removed unrelated details/attributes");
            require(disabledLines.stream().anyMatch(line -> line.getString().equals(SOURCE_LORE.getString())),
                    "Source lore was hidden along with Create descriptions");
            AllConfigs.client().tooltips.set(true);
        }
    }

    private static List<ItemStack> mixedPair(ItemStack source) {
        if (source.is(AllItems.CARDBOARD_LEGGINGS)) {
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
            return List.of(pair.primary(), pair.inverse());
        }
        return BootsCoexistence.pair(source, new ItemStack(Items.IRON_BOOTS));
    }

    private static int sectionStart(List<Component> lines, ItemStack root, boolean left) {
        String product = BuiltInRegistries.ITEM.getKey(root.getItem()).getNamespace();
        String item = product.equals("mismatchedleggings") ? "mixed_leggings" : "mixed_boots";
        String prefix = Component.translatable("tooltip." + product + "." + item + (left ? ".left" : ".right")).getString();
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).getString().startsWith(prefix)) {
                return index;
            }
        }
        throw new AssertionError("Source section was missing");
    }

    private static List<Component> contribution(ItemStack source, Minecraft client) {
        var modifier = TooltipModifier.REGISTRY.get(source.getItem());
        require(modifier != null, "Native tooltip registry has no Cardboard/Diving entry");
        var placeholder = Component.literal("native-name-placeholder");
        var lines = new ArrayList<Component>();
        lines.add(placeholder);
        modifier.modify(lines, client.player);
        require(lines.getFirst() == placeholder, "Native modifier did not preserve the name line");
        lines.removeFirst();
        return List.copyOf(lines);
    }

    private static List<Component> tooltip(Minecraft client, ItemStack stack, TooltipFlag flag) {
        hookCalls = 0;
        hookStack = null;
        var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
        require(hookCalls == 1 && hookStack == stack && lines.stream().filter(FOREIGN_HOOK::equals).count() == 1,
                "Source rendering recursively replayed a foreign final tooltip hook");
        return lines;
    }

    private static int occurrences(List<Component> lines, List<Component> expected) {
        require(!expected.isEmpty(), "Cannot count an empty native contribution");
        int count = 0;
        for (int start = 0; start + expected.size() <= lines.size(); start++) {
            if (lines.subList(start, start + expected.size()).equals(expected)) {
                count++;
            }
        }
        return count;
    }

    private static int subsequence(List<Component> lines, List<Component> expected) {
        for (int start = 0; start + expected.size() <= lines.size(); start++) {
            if (lines.subList(start, start + expected.size()).equals(expected)) {
                return start;
            }
        }
        return -1;
    }
}
