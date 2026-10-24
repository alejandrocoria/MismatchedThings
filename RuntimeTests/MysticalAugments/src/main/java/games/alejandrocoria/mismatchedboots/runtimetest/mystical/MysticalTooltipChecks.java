package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.require;

/** Real client/native components; no candidate producer or translated string classifier. */
final class MysticalTooltipChecks {
    private MysticalTooltipChecks() {}

    static List<ItemStack> run(Minecraft client) throws Exception {
        var weak = MysticalAugmentsProbe.item("awakened_supremium_boots", "HEALTH_BOOST_I", "STEP_ASSIST");
        var strong = MysticalAugmentsProbe.item("awakened_supremium_boots", "HEALTH_BOOST_V", "JUMP_BOOST_III");
        weak.set(DataComponents.CUSTOM_NAME, Component.literal("MA source purchase"));
        var pair = MixedBootsFactory.createPair(weak, strong);
        Component heading = Component.translatable("tooltip.mysticalagriculture.augments").withStyle(ChatFormatting.GRAY);
        var weakLines = nativeLines(client, weak);
        var strongLines = nativeLines(client, strong);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var before = root.copy();
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                int last = lines.lastIndexOf(TooltipSeparator.INSTANCE);
                require(last >= 0, "Source/root separator lost");
                var rootLines = lines.subList(last + 1, lines.size());
                require(weakLines.stream().noneMatch(rootLines::contains) && strongLines.stream().noneMatch(rootLines::contains),
                        "Native augment information duplicated in root");
                require(Collections.indexOfSubList(lines.subList(0, last), weakLines) >= 0
                        && Collections.indexOfSubList(lines.subList(0, last), strongLines) >= 0,
                        "Source physical purchases were replaced by the effective union");
                require(lines.stream().filter(heading::equals).count() == 2, "Expected exactly one native augment list per source");
                require(lines.stream().anyMatch(line -> line.getString().contains("MA source purchase")), "Source custom name lost");
                require(ItemStack.matches(before, root), "Tooltip changed complete root/source history");
            }
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
            var hidden = root.copy(); hidden.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(type, true));
            var hiddenLines = hidden.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
            int last = hiddenLines.lastIndexOf(TooltipSeparator.INSTANCE);
            require(hiddenLines.subList(last + 1, hiddenLines.size()).stream().noneMatch(heading::equals), "Root augmented list unexpectedly rendered");
            require(Collections.indexOfSubList(hiddenLines.subList(0, last), weakLines) >= 0
                    && Collections.indexOfSubList(hiddenLines.subList(0, last), strongLines) >= 0, "Root component visibility changed native source lists");
            hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            require(hidden.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).isEmpty(), "Hidden root tooltip rendered");
        }
        var empty = MixedBootsFactory.createPair(MysticalAugmentsProbe.item("inferium_boots"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        var emptyLines = empty.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        require(emptyLines.subList(emptyLines.lastIndexOf(TooltipSeparator.INSTANCE) + 1, emptyLines.size()).stream()
                .noneMatch(heading::equals), "Empty effective union created a fake root slot");
        var hiddenSource = weak.copy(); hiddenSource.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        var hiddenRoot = MixedBootsFactory.createPair(hiddenSource, strong).primary(); hiddenRoot.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        var sourceLines = hiddenRoot.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        require(sourceLines.stream().filter(heading::equals).count() == 1, "Source hide-tooltip lost its owner boundary");
        var visual = pair.primary(); visual.set(DataComponents.CUSTOM_NAME, Component.literal("Mixed source augments"));
        return List.of(strong, visual);
    }

    private static List<Component> nativeLines(Minecraft client, ItemStack source) {
        var lines = new ArrayList<Component>();
        MysticalNativeTestApi.call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "addAugmentListToTooltip",
                new Class<?>[]{Consumer.class, ItemStack.class, int.class, Player.class}, (Consumer<Component>) lines::add, source, 2, client.player);
        return lines;
    }
}
