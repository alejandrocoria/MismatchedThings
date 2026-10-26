package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Independent native equipment event only, not another implementation of the production source adapter. */
final class SilentGearNativeTooltipControl {
    private static final Set<String> DIAGNOSTICS = Set.of("misc.silentgear.broken", "misc.silentgear.exampleOutput1",
                                                          "misc.silentgear.exampleOutput2", "misc.silentgear.invalidParts");

    private SilentGearNativeTooltipControl() {
    }

    static List<Component> details(Minecraft client, ItemStack source, TooltipFlag flag) throws Exception {
        var props = NeoForgeSilentGearTest.NativeApi.component("GEAR_PROPERTIES");
        var construction = NeoForgeSilentGearTest.NativeApi.component("GEAR_CONSTRUCTION");
        Class<?> gear = Class.forName("net.silentchaos512.gear.api.item.GearItem");
        var display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (!gear.isInstance(source.getItem()) || !source.has(props) || !source.has(construction) || display.hideTooltip()) {
            return List.of();
        }

        boolean showProps = display.shows(props);
        boolean showParts = display.shows(construction);
        if (!showProps && !showParts) {
            return List.of();
        }

        Class<?> flags = Class.forName("net.silentchaos512.gear.client.util.GearTooltipFlag");
        Object nativeFlag = flags.getMethod("withModifierKeys", boolean.class, boolean.class, boolean.class)
                                 .invoke(null, flag.isAdvanced(), showProps, showParts);
        var lines = new ArrayList<Component>();
        var event = new ItemTooltipEvent(source.copy(), client.player, lines, (TooltipFlag) nativeFlag, Item.TooltipContext.of(client.level));
        Class.forName("net.silentchaos512.gear.client.tooltips.GearItemTooltips")
             .getMethod("onTooltip", ItemTooltipEvent.class).invoke(null, event);
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && DIAGNOSTICS.contains(text.getKey()));
        if (!showProps || !showParts) {
            // Hidden-group probes deliberately use plain main+lining, whose native part summaries are empty.
            // Do not silently generalize this oracle into a second summary/visibility renderer.
            Object data = source.get(construction);
            Object parts = data.getClass().getMethod("parts").invoke(data);
            var summaries = new ArrayList<Component>();
            Class<?> partClass = Class.forName("net.silentchaos512.gear.gear.part.PartInstance");
            for (Object part : (Collection<?>) parts.getClass().getMethod("toSortedList").invoke(parts)) {
                partClass.getMethod("addInformation", ItemStack.class, List.class, TooltipFlag.class)
                         .invoke(part, source.copy(), summaries, nativeFlag);
            }
            require(summaries.isEmpty(), "Hidden-group oracle requires an explicit summary-free native control");
            Class<?> keys = Class.forName("net.silentchaos512.gear.client.KeyTracker");
            Object vanilla = Class.forName("net.silentchaos512.gear.Config$Client").getField("vanillaStyleTooltips").get(null);
            if (!showProps && !(boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null)
                    && !(boolean) vanilla.getClass().getMethod("get").invoke(vanilla)) {
                require(!lines.isEmpty(), "Missing native compact-traits bug control");
                lines.removeFirst(); // Native's false showProperties flag still emits this one compact trait row.
            }
        }

        return List.copyOf(lines);
    }
}
