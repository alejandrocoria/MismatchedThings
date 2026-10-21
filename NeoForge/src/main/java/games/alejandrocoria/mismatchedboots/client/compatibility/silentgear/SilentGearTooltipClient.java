package games.alejandrocoria.mismatchedboots.client.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Native original-source presentation only: no root totals, gear event replay or gameplay readiness. */
public final class SilentGearTooltipClient {
    private static Bindings bindings;
    private static boolean disabled, warnedData;

    private SilentGearTooltipClient() {}

    public static void appendSource(ItemStack input, TooltipFlag flag, Consumer<Component> output) {
        if (disabled) { return; }
        List<Component> lines;
        try {
            if (bindings == null) { bindings = new Bindings(); }
            lines = bindings.details(input, flag);
        } catch (Throwable failure) {
            while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
                failure = invocation.getCause();
            }
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            if (failure instanceof SourceDataException) {
                if (!warnedData) {
                    warnedData = true;
                    Constants.LOG.warn("Silent Gear source tooltip data unavailable; affected source details omitted", failure);
                }
            } else {
                disabled = true;
                Constants.LOG.warn("Silent Gear source tooltips disabled: native presentation unavailable", failure);
            }
            return;
        }
        // Native part summaries insert into their list. Publish only the completed source-local block.
        lines.forEach(output);
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + name);
    }

    /** Only explicit source validation failures are local; formatter/API exceptions revoke presentation. */
    private static final class SourceDataException extends RuntimeException {
        private SourceDataException(String message) { super(message); }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(String name) {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("silentgear", name)).orElseThrow().value();
    }

    private static final class Bindings {
        private final Class<?> gear = nativeClass("api.item.GearItem"), flagsType = nativeClass("client.util.GearTooltipFlag");
        private final Class<?> constructionType = nativeClass("core.component.GearConstructionData");
        private final Class<?> propertiesType = nativeClass("core.component.GearPropertiesData");
        private final Class<?> partType = nativeClass("gear.part.PartInstance"), partListType = nativeClass("api.part.PartList");
        private final Class<?> helpers = nativeClass("client.tooltips.GearItemTooltips"), keys = nativeClass("client.KeyTracker");
        private final DataComponentType<Object> construction = component("construction"), properties = component("properties");
        private final Method flags = flagsType.getMethod("withModifierKeys", boolean.class, boolean.class, boolean.class);
        private final Method getParts = constructionType.getMethod("parts"), sorted = partListType.getMethod("toSortedList");
        private final Method mains = partListType.getMethod("getMains"), propertyValues = propertiesType.getMethod("properties");
        private final Method validPart = partType.getMethod("isValid");
        private final Method partInfo = partType.getMethod("addInformation", ItemStack.class, List.class, TooltipFlag.class);
        private final Method propertyInfo = helpers.getMethod("addPropertiesInfo", ItemStack.class, List.class, flagsType, gear);
        private final Method partsInfo = helpers.getMethod("tooltipListParts", ItemStack.class, List.class, Collection.class, flagsType);
        private final Method constructionDown = keys.getMethod("isDisplayConstructionDown");
        private final Object constructionKey = keys.getField("DISPLAY_CONSTRUCTION").get(null);
        private final Method keyText = nativeClass("util.TextUtil").getMethod("keyBinding", Class.forName("net.minecraft.client.KeyMapping"));
        private final Object vanilla = nativeClass("Config$Client").getField("vanillaStyleTooltips").get(null);
        private final Method vanillaValue = vanilla.getClass().getMethod("get");
        private final Class<?> color = Class.forName("net.silentchaos512.lib.util.Color");
        private final int gold = (int) color.getMethod("getColor").invoke(color.getField("GOLD").get(null)) & 0xFFFFFF;

        private Bindings() throws ReflectiveOperationException {}

        private List<Component> details(ItemStack input, TooltipFlag flag) throws ReflectiveOperationException {
            var equipment = input.get(DataComponents.EQUIPPABLE);
            var display = input.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
            if (input.getCount() != 1 || !gear.isInstance(input.getItem()) || equipment == null
                    || equipment.slot() != EquipmentSlot.FEET || display.hideTooltip()
                    || !constructionType.isInstance(input.get(construction)) || !propertiesType.isInstance(input.get(properties))) {
                return List.of();
            }
            boolean showParts = display.shows(construction), showProperties = display.shows(properties);
            if (!showParts && !showProperties) { return List.of(); }
            ItemStack source = input.copy();
            if (((java.util.Map<?, ?>) propertyValues.invoke(source.get(properties))).isEmpty()) { return List.of(); }
            Object parts = getParts.invoke(source.get(construction));
            if (!partListType.isInstance(parts)) { throw new SourceDataException("Invalid SG construction part list"); }
            // Native sorting filters invalid references out. Validate raw provenance before that lossy view.
            for (Object part : (Collection<?>) parts) {
                if (!partType.isInstance(part) || !(boolean) validPart.invoke(part)) {
                    throw new SourceDataException("Invalid SG source part");
                }
            }
            if (((Collection<?>) mains.invoke(parts)).isEmpty()) { return List.of(); }
            var ordered = (Collection<?>) sorted.invoke(parts);
            Object nativeFlag = flags.invoke(null, flag.isAdvanced(), showProperties, showParts);
            var result = new ArrayList<Component>();
            if (!(boolean) vanillaValue.invoke(vanilla)) {
                if (showParts) { for (Object part : ordered) { partInfo.invoke(part, source, result, nativeFlag); } }
                // Native emits compact traits even when showProperties=false; the whole call must be gated.
                if (showProperties) { propertyInfo.invoke(null, source, result, nativeFlag, source.getItem()); }
            }
            if (showParts) {
                MutableComponent header = Component.translatable("misc.silentgear.tooltip.construction").withColor(gold);
                if ((boolean) constructionDown.invoke(null)) {
                    result.add(header);
                    partsInfo.invoke(null, source, result, ordered, nativeFlag);
                } else {
                    Component hint = ((MutableComponent) keyText.invoke(null, constructionKey)).withStyle(ChatFormatting.GRAY);
                    result.add(header.append(Component.literal(" ").append(hint)));
                }
            }
            return List.copyOf(result);
        }
    }
}
