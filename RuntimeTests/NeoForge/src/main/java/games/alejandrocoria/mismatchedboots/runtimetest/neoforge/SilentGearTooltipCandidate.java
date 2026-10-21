package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Disposable client candidate: native source helpers only, no heading/event replay/root projection. */
public final class SilentGearTooltipCandidate {
    static boolean enabled = true, refused;
    static TooltipFlag flag = TooltipFlag.NORMAL;
    static Thread observing;
    static int recalculations;
    private static Bindings bindings;

    private SilentGearTooltipCandidate() {}

    /** Fixture-only scope bridge; production should pass this argument through the local source callback. */
    public static void captureFlag(TooltipFlag value) { flag = value; }

    public static void recalculation() {
        if (Thread.currentThread() == observing) {
            recalculations++;
            throw new AssertionError("Tooltip entered native recalculation");
        }
    }

    public static void append(ItemStack source, Consumer<Component> output) {
        if (!enabled) { return; }
        try { details(source).forEach(output); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Tooltip candidate binding failed", failure); }
    }

    static List<Component> details(ItemStack input) throws ReflectiveOperationException {
        if (refused) { return List.of(); }
        if (bindings == null) { bindings = new Bindings(); }
        Bindings b = bindings;
        if (!b.gear.isInstance(input.getItem()) || !input.has(b.construction) || !input.has(b.properties)) { return List.of(); }
        var display = input.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (display.hideTooltip()) { return List.of(); }
        boolean showParts = display.shows(b.construction), showProperties = display.shows(b.properties);
        if (!showParts && !showProperties) { return List.of(); }
        ItemStack source = input.copy();
        Object flags = b.flags.invoke(null, flag.isAdvanced(), showProperties, showParts);
        Object parts = b.getParts.invoke(source.get(b.construction));
        var sorted = (Collection<?>) b.sorted.invoke(parts);
        var result = new ArrayList<Component>();
        boolean vanilla = (boolean) b.vanilla.getClass().getMethod("get").invoke(b.vanilla);
        if (!vanilla) {
            if (showParts) { for (Object part : sorted) { b.partInfo.invoke(part, source, result, flags); } }
            // The native helper emits compact traits even with showProperties=false; gate it externally.
            if (showProperties) { b.propertyInfo.invoke(null, source, result, flags, source.getItem()); }
        }
        if (showParts) {
            MutableComponent header = Component.translatable("misc.silentgear.tooltip.construction").withColor(b.gold);
            if ((boolean) b.constructionDown.invoke(null)) {
                result.add(header);
                b.partsInfo.invoke(null, source, result, sorted, flags);
            } else {
                Component key = ((MutableComponent) b.keyText.invoke(null, b.constructionKey)).withStyle(ChatFormatting.GRAY);
                result.add(header.append(Component.literal(" ").append(key)));
            }
        }
        return List.copyOf(result);
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + name);
    }

    private static final class Bindings {
        final Class<?> gear = nativeClass("api.item.GearItem"), flagsType = nativeClass("client.util.GearTooltipFlag");
        final Class<?> helper = nativeClass("client.tooltips.GearItemTooltips"), keys = nativeClass("client.KeyTracker");
        final DataComponentType<Object> construction = NeoForgeSilentGearTest.NativeApi.component("GEAR_CONSTRUCTION");
        final DataComponentType<Object> properties = NeoForgeSilentGearTest.NativeApi.component("GEAR_PROPERTIES");
        final Method flags = flagsType.getMethod("withModifierKeys", boolean.class, boolean.class, boolean.class);
        final Method getParts = nativeClass("core.component.GearConstructionData").getMethod("parts");
        final Method sorted = nativeClass("api.part.PartList").getMethod("toSortedList");
        final Method partInfo = nativeClass("gear.part.PartInstance").getMethod("addInformation", ItemStack.class, List.class, TooltipFlag.class);
        final Method propertyInfo = helper.getMethod("addPropertiesInfo", ItemStack.class, List.class, flagsType, gear);
        final Method partsInfo = helper.getMethod("tooltipListParts", ItemStack.class, List.class, Collection.class, flagsType);
        final Method constructionDown = keys.getMethod("isDisplayConstructionDown");
        final Object constructionKey = keys.getField("DISPLAY_CONSTRUCTION").get(null);
        final Method keyText = nativeClass("util.TextUtil").getMethod("keyBinding", Class.forName("net.minecraft.client.KeyMapping"));
        final Object vanilla = nativeClass("Config$Client").getField("vanillaStyleTooltips").get(null);
        final Class<?> color = Class.forName("net.silentchaos512.lib.util.Color");
        final int gold = (int) color.getMethod("getColor").invoke(color.getField("GOLD").get(null)) & 0xFFFFFF;

        Bindings() throws ReflectiveOperationException {}
    }
}
