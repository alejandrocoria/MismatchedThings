package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class BetterEndClientChecks {
    private static int ticks;
    private static boolean finished;

    private BetterEndClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1800, "Better End client startup timed out: screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                return;
            }
            var nativeLeggings = BetterEndChecks.nativeStack("crystalite_leggings");
            var roots = BetterEndChecks.pair(nativeLeggings, new ItemStack(Items.IRON_LEGGINGS));
            for (var stack : List.of(nativeLeggings, roots.getFirst(), roots.getLast())) {
                var before = stack.copy();
                BetterEndChecks.equipSet(client.player, stack, BetterEndChecks.nativeStack("crystalite_boots"));
                tooltip(client, stack, true);
                client.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                tooltip(client, stack, false);
                require(ItemStack.matches(before, stack), "Native tooltip mutated the root");
            }
            for (var root : BetterEndChecks.pair(nativeLeggings, nativeLeggings)) {
                BetterEndChecks.equipSet(client.player, root, BetterEndChecks.nativeStack("crystalite_boots"));
                tooltip(client, root, true);
            }
            if (Boolean.getBoolean("leggings.test.boots")) {
                var boots = BootsCoexistence.pair(BetterEndChecks.nativeStack("crystalite_boots"), new ItemStack(Items.IRON_BOOTS));
                for (var bootRoot : boots) {
                    for (var root : roots) {
                        BetterEndChecks.equipSet(client.player, root, bootRoot);
                        tooltip(client, root, true);
                    }
                }
            }
            BetterEndChecks.clearArmor(client.player);
            for (var root : roots) {
                tooltip(client, root, false);
            }
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void tooltip(Minecraft client, ItemStack stack, boolean complete) {
        var context = Item.TooltipContext.of(client.level);
        String description = Component.translatable("tooltip.armor.crystalite_leggings").getString();
        String setBonus = Component.translatable("tooltip.armor.crystalite_set").getString();
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            var lines = stack.getTooltipLines(context, client.player, flag);
            var data = MixedLeggingsStructure.validData(stack).orElse(null);
            require(lines.stream().filter(line -> line.getString().equals(description)).count() == (data == null ? 1 : 0),
                    "Fixed health description leaked into mixed sources or disappeared from native leggings");
            if (data != null) {
                var sources = new ArrayList<Component>();
                MixedLeggingsTooltipFormatter.appendSourceSections(data, context, flag, sources::add);
                require(sources.stream().noneMatch(line -> !(line instanceof TooltipSeparator) && line.getString().isEmpty()),
                        "Removing the source health description left an empty spacer");
                var control = new ItemStack(Items.IRON_LEGGINGS);
                control.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.get(DataComponents.ATTRIBUTE_MODIFIERS));
                String health = Component.translatable("attribute.name.max_health").getString();
                var expectedHealth = control.getTooltipLines(context, client.player, flag).stream()
                        .filter(line -> line.getString().contains(health)).toList();
                require(expectedHealth.size() == 1 && lines.stream().filter(expectedHealth::contains).count() == 1,
                        "Effective root health tooltip differs from the native attribute rendering");
            }
            var bonusLines = lines.stream().filter(line -> line.getString().equals(setBonus)).toList();
            require(bonusLines.size() == 1, "Native root set-bonus tooltip disappeared or was duplicated");
            var style = bonusLines.getFirst().getStyle();
            require(style.isItalic() && style.getColor() != null
                            && style.getColor().getValue() == (complete ? ChatFormatting.BLUE : ChatFormatting.DARK_GRAY).getColor(),
                    "Native set tooltip does not reflect the equipped full-set state");
            var withoutPlayer = stack.getTooltipLines(context, null, flag);
            require(withoutPlayer.stream().anyMatch(line -> line.getString().equals(setBonus)
                            && line.getStyle().getColor() != null
                            && line.getStyle().getColor().getValue() == ChatFormatting.DARK_GRAY.getColor()),
                    "Native tooltip without a player lost its inactive set bonus");
        }
    }
}
