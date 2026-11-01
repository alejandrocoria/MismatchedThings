package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class PresentationChecks {
    private static final String LEFT = "tooltip.mismatchedleggings.mixed_leggings.left";
    private static final String RIGHT = "tooltip.mismatchedleggings.mixed_leggings.right";

    private PresentationChecks() {
    }

    static ItemStack verify(Minecraft client) {
        var sources = RenderFixture.sources(2);
        sources[0].set(DataComponents.CUSTOM_NAME, Component.literal("Twilight"));
        sources[0].set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("First source lore"), Component.empty(), Component.literal("After native blank"))));
        sources[0].set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        sources[1].set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Second source lore"))));
        var swift = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SWIFT_SNEAK);
        sources[0].enchant(swift, 3);
        var pair = MixedLeggingsFactory.createPair(sources[0], sources[1], CompositionRules.defaults());
        var context = Item.TooltipContext.of(client.level);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var live = new ItemEnchantments.Mutable(root.getEnchantments());
            live.set(swift, 2);
            root.set(DataComponents.ENCHANTMENTS, live.toImmutable());
            root.setDamageValue(17);
            var before = root.copy();
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = root.getTooltipLines(context, client.player, flag);
                int left = index(lines, LEFT);
                int right = index(lines, RIGHT);
                int first = lines.indexOf(TooltipSeparator.INSTANCE);
                int last = lines.lastIndexOf(TooltipSeparator.INSTANCE);
                require(left == 1 && left < first && first < right && right < last, "Source sections are not ordered");
                require(lines.stream().filter(TooltipSeparator.class::isInstance).count() == 2, "Wrong structural separator count");
                require(lines.get(left).getSiblings().getFirst().getStyle().isBold()
                        && lines.get(left).getStyle().getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.GRAY)),
                        "Source header lost its native family style");
                require(lines.get(left).getString().contains(data.left().source().getItemName().getString())
                        && lines.get(right).getString().contains(data.right().source().getItemName().getString()),
                        "Natural source identity was lost");
                require(count(lines, "enchantment.minecraft.swift_sneak") == 1
                        && lines.indexOf(Enchantment.getFullname(swift, 2)) > last, "Source enchantments duplicated or replaced live-root state");
                require(index(lines, "item.modifiers.legs") > last && index(lines, "item.modifiers.feet") < 0,
                        "Effective attributes are not in the root's legs section");
                require(count(lines, "item.unbreakable") == 0, "A source-only gameplay property leaked into the tooltip");
                require(lines.stream().anyMatch(line -> line.getString().contains("First source lore"))
                        && lines.stream().anyMatch(line -> line.getString().contains("Second source lore")), "Source lore disappeared");
                int blank = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).getString().contains("First source lore")) { blank = i + 1; }
                }
                require(blank > 0 && lines.get(blank).getString().isEmpty()
                        && !(lines.get(blank) instanceof TooltipSeparator), "Native source blank became a structural separator");
                var trim = new ArrayList<Component>();
                sources[0].addToTooltip(DataComponents.TRIM, context, TooltipDisplay.DEFAULT, trim::add, flag);
                require(!trim.isEmpty() && lines.containsAll(trim), "Native trim components/styles were not retained");
                if (flag.isAdvanced()) {
                    sourceId(lines, left + 1, data.left().source());
                    sourceId(lines, right + 1, data.right().source());
                    require(count(lines, "item.components") == 1 && count(lines, "item.durability") == 1,
                            "Nested stack diagnostics leaked into advanced tooltip");
                } else {
                    require(lines.stream().noneMatch(line -> line.getString().equals("minecraft:leather_leggings")
                            || line.getString().equals("minecraft:golden_leggings")), "Source IDs appeared without F3+H");
                }
                for (var line : lines) {
                    require(line.copy().getString().equals(line.getString()), "Copying structured tooltip changed its text");
                }
            }
            require(ItemStack.matches(root, before), "Tooltip generation mutated live state or provenance");
        }

        var hidden = sources[0].copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        var root = MixedLeggingsFactory.createPair(hidden, sources[1], CompositionRules.defaults()).primary();
        var lines = root.getTooltipLines(context, client.player, TooltipFlag.ADVANCED);
        require(lines.stream().noneMatch(line -> line.getString().equals("minecraft:leather_leggings")
                || line.getString().contains("First source lore")), "Hidden source details were exposed");
        require(index(lines, LEFT) >= 0 && index(lines, RIGHT) >= 0, "Hidden details erased pair identity");
        root.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        require(root.getTooltipLines(context, client.player, TooltipFlag.NORMAL).isEmpty(), "Native root hide-tooltip was bypassed");

        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        var diagnostic = bare.getTooltipLines(context, client.player, TooltipFlag.NORMAL);
        require(index(diagnostic, "tooltip.mismatchedleggings.mixed_leggings.invalid") >= 0
                && index(diagnostic, "tooltip.mismatchedleggings.mixed_leggings.bare_hint") >= 0
                && index(diagnostic, LEFT) < 0, "Bare diagnostic invented sources or omitted its acquisition hint");
        var malformed = pair.primary().copyWithCount(2);
        diagnostic = malformed.getTooltipLines(context, client.player, TooltipFlag.NORMAL);
        require(index(diagnostic, "tooltip.mismatchedleggings.mixed_leggings.invalid") >= 0
                && index(diagnostic, LEFT) < 0 && index(diagnostic, RIGHT) < 0, "Malformed diagnostic invented sources");
        return pair.primary();
    }

    private static void sourceId(List<Component> lines, int index, ItemStack source) {
        var line = lines.get(index);
        require(line.getString().equals(BuiltInRegistries.ITEM.getKey(source.getItem()).toString())
                && TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY).equals(line.getStyle().getColor()),
                "Advanced source ID is misplaced or incorrectly styled");
    }

    private static int index(List<Component> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (hasKey(lines.get(i), key)) { return i; }
        }
        return -1;
    }

    private static long count(List<Component> lines, String key) {
        return lines.stream().filter(line -> hasKey(line, key)).count();
    }

    private static boolean hasKey(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(sibling -> hasKey(sibling, key));
    }
}
