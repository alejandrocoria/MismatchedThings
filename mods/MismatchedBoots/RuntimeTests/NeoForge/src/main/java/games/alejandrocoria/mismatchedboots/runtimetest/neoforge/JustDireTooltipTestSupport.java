package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Shared tooltip observation only: no replacement presentation, projection or gameplay driver. */
public final class JustDireTooltipTestSupport {
    private static Boolean shift;

    private JustDireTooltipTestSupport() {
    }

    public static Boolean shift() {
        return shift;
    }

    static List<Component> capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean expanded) {
        Boolean previous = shift;
        shift = expanded;
        try {
            require(client.hasShiftDown() == expanded, "Controlled tooltip Shift input was not installed");
            return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
        } finally {
            shift = previous;
        }
    }

    static String key(Component line) {
        return line.getContents() instanceof TranslatableContents text ? text.getKey() : "";
    }

    static void energy(List<Component> lines, int amount, int capacity, boolean expanded) {
        var energy = lines.stream().filter(line -> key(line).equals("justdirethings.festored")).toList();
        require(energy.size() == 1, "Missing/duplicate root FE line");
        var args = ((TranslatableContents) energy.getFirst().getContents()).getArgs();
        try {
            var magic = Class.forName("com.direwolf20.justdirethings.util.MagicHelpers");
            Object expectedAmount = expanded ? magic.getMethod("formatted", int.class).invoke(null, amount)
                                             : magic.getMethod("tidyValue", float.class).invoke(null, (float) amount);
            Object expectedCapacity = expanded ? magic.getMethod("formatted", int.class).invoke(null, capacity)
                                               : magic.getMethod("tidyValue", float.class).invoke(null, (float) capacity);
            require(args.length == 2 && args[0].equals(expectedAmount) && args[1].equals(expectedCapacity),
                    "Live root FE differs from native precision/formatting: " + energy);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        require(TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(energy.getFirst().getStyle().getColor()), "FE color differs from native");
    }

    static Component ability(List<Component> lines, String name, boolean enabled) {
        var rows = lines.stream().filter(line -> key(line).equals("justdirethings.ability." + name)).toList();
        require(rows.size() == 1, "Missing/duplicate root ability " + name);
        var row = rows.getFirst();
        require(((TranslatableContents) row.getContents()).getArgs().length == 0 && row.getSiblings().isEmpty()
                        && TextColor.fromLegacyFormat(enabled ? ChatFormatting.GREEN : ChatFormatting.DARK_RED).equals(row.getStyle().getColor()),
                "Installed/intrinsic ability has stale numeric/status text or wrong individual color: " + row);
        return row;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
