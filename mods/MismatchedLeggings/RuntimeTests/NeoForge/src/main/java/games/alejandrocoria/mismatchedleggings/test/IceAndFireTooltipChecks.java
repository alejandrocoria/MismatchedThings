package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Compare actual native producers, including Component styles, within each source section. */
public final class IceAndFireTooltipChecks {
    private IceAndFireTooltipChecks() {}

    @SuppressWarnings("deprecation")
    public static void verify() {
        int variants = 0, descriptions = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_leggings")) { continue; }
            variants++;
            var source = new ItemStack(item);
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var nativeLines = new ArrayList<Component>();
                item.appendHoverText(source, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, nativeLines::add, flag);
                descriptions += nativeLines.size();
                for (var root : List.of(pair.primary(), pair.inverse(), MixedLeggingsFactory.createPair(source, source).primary())) {
                    var before = root.copy();
                    var lines = root.getTooltipLines(Item.TooltipContext.EMPTY, null, flag);
                    int first = lines.indexOf(TooltipSeparator.INSTANCE), last = lines.lastIndexOf(TooltipSeparator.INSTANCE);
                    require(first > 0 && last > first, "Missing source sections: " + id);
                    var data = games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure.validData(root).orElseThrow();
                    for (var line : nativeLines) {
                        int leftCount = data.left().source().is(item) ? 1 : 0;
                        int rightCount = data.right().source().is(item) ? 1 : 0;
                        require(lines.subList(0, first).stream().filter(line::equals).count() == leftCount
                                        && lines.subList(first + 1, last).stream().filter(line::equals).count() == rightCount
                                        && lines.subList(last + 1, lines.size()).stream().noneMatch(line::equals),
                                "Native description/style not confined to its source: " + id + " / " + flag);
                    }
                    require(ItemStack.matches(before, root) && lines.equals(root.getTooltipLines(Item.TooltipContext.EMPTY, null, flag)),
                            "Tooltip mutated history or accumulated lines: " + id);
                }
            }
        }
        require(variants == 31 && descriptions > 0, "Incomplete native tooltip controls");
        try {
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "ice-and-fire-tooltip.txt"),
                    "31 native producers; normal/advanced, both orientations and duplicates; exact styled source descriptions, no root duplicate; immutable history\n");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
