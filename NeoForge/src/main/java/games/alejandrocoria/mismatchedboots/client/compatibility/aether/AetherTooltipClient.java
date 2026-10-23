package games.alejandrocoria.mismatchedboots.client.compatibility.aether;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipState;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Original source tiers and current player set presentation; no holder, attribute or equipment writes. */
public final class AetherTooltipClient {
    private static final List<String> FAMILIES = List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune", "sentry");
    private static SetBindings sets;
    private static DataComponentType<Object> tierType;
    private static Class<?> tierClass;
    private static boolean setsDisabled, tiersDisabled, warnedTierData;

    private AetherTooltipClient() {}

    public static void sourceSet(ItemStack source, Consumer<Component> output) {
        if (setsDisabled || !AetherTooltipState.attributesReady() || !BootHalf.isValidSource(source)) { return; }
        List<Component> lines;
        try {
            if (sets == null) { sets = new SetBindings(); }
            var display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
            if (display.hideTooltip() || !display.shows(sets.armorSet)) { return; }
            lines = sourceLines(source);
        } catch (Throwable failure) {
            disableSets(failure); return;
        }
        // Keep native containment active after formatting failure: falling back would borrow another pair's bonuses.
        lines.forEach(output);
    }

    public static void attributes(AddAttributeTooltipsEvent event) {
        var display = event.getContext().tooltipDisplay(); var root = event.getStack();
        if (setsDisabled || !AetherTooltipState.attributesReady() || display.hideTooltip() || !event.shouldShow()
                || !AetherTooltipState.managed(root)) { return; }
        List<Component> lines;
        try {
            if (sets == null) { sets = new SetBindings(); }
            if (!display.shows(sets.armorSet)) { return; }
            lines = attributeLines(root, event.getContext().flag());
        } catch (Throwable failure) {
            disableSets(failure); return;
        }
        // This event is after ordinary attributes, including for the nested source path; only real FEET qualify.
        lines.forEach(line -> event.addTooltipLines(line));
    }

    public static void sourceTier(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (tiersDisabled || !AetherTooltipState.tiersReady() || !BootHalf.isValidSource(source)) { return; }
        List<Component> lines = new ArrayList<>();
        try {
            if (tierType == null) {
                tierType = component("reinforcement_tier");
                tierClass = Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier");
            }
            var display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
            if (display.hideTooltip() || !display.shows(tierType)) { return; }
            Object tier = source.get(tierType);
            if (tier == null) { return; }
            if (!tierClass.isInstance(tier) || !(tier instanceof TooltipProvider provider)) {
                if (!warnedTierData) {
                    warnedTierData = true; Constants.LOG.warn("Aether II source tier unavailable: invalid native tier data");
                }
                return;
            }
            // The audited native enum only formats its own localized tier; it does not use a world/player.
            provider.addToTooltip(Item.TooltipContext.EMPTY, lines::add, flag, source);
        } catch (Throwable failure) {
            failure = unwrap(failure); tiersDisabled = true;
            Constants.LOG.warn("Aether II tier tooltips disabled: native presentation unavailable", failure); return;
        }
        lines.forEach(output);
    }

    private static String family(ItemStack source) {
        for (var family : FAMILIES) {
            if (source.getItem() == BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("aether_ii", family + "_boots"))
                    && TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("aether_ii", "armor/" + family)).equals(source.get(sets.armorSet))) {
                return family;
            }
        }
        return null;
    }

    private static List<Component> sourceLines(ItemStack source) throws ReflectiveOperationException {
        String family = family(source);
        var lines = new ArrayList<Component>();
        if (family == null) { return lines; }
        String key = source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.";
        if (!family.equals("arkenium")) { lines.add(Component.translatable(key + "1")); }
        var client = Minecraft.getInstance();
        // Set progress describes the player's current equipment, even when this source is only being previewed.
        if (!family.equals("sentry") && client.player != null && client.level != null) {
            int count = Math.min(3, (int) sets.count.invoke(null, client.player, source.get(sets.armorSet)));
            lines.add(Component.translatable(key + "2", Component.literal(count + "/3")
                    .withStyle(count == 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<Component> attributeLines(ItemStack root, TooltipFlag flag) throws ReflectiveOperationException {
        var client = Minecraft.getInstance(); var player = client.player;
        var lines = new ArrayList<Component>();
        // A current equipment count is not proof that a hovered copy contributes the player's active modifiers.
        if (player == null || client.level == null || root != player.getItemBySlot(EquipmentSlot.FEET)) { return lines; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        var families = new LinkedHashSet<String>();
        for (var source : List.of(data.left().source(), data.right().source())) {
            String family = family(source);
            if (family != null && !family.equals("sentry")) { families.add(family); }
        }
        var attributes = (Map<Holder<Attribute>, AttributeInstance>) sets.attributes.invoke(player.getAttributes());
        for (var family : families) {
            for (var attribute : attributes.entrySet()) {
                for (var modifier : attribute.getValue().getModifiers()) {
                    if (modifier.id().getNamespace().equals("aether_ii")
                            && modifier.id().getPath().startsWith("armor_set.ability." + family + ".")) {
                        lines.add(attribute.getKey().value().toComponent(modifier, flag));
                    }
                }
            }
        }
        return lines;
    }

    private static void disableSets(Throwable failure) {
        failure = unwrap(failure); setsDisabled = true;
        Constants.LOG.warn("Aether II set tooltips disabled: native presentation unavailable", failure);
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) { failure = invocation.getCause(); }
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        return failure;
    }
    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(String name) {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(
                Identifier.fromNamespaceAndPath("aether_ii", name)).orElseThrow();
    }
    private static final class SetBindings {
        private final DataComponentType<Object> armorSet = component("armor_set");
        private final Method count = Class.forName("com.aetherteam.aetherii.item.equipment.EquipmentUtil")
                .getMethod("getArmorCount", LivingEntity.class, TagKey.class);
        private final Method attributes = Class.forName("com.aetherteam.aetherii.mixin.mixins.common.accessor.AttributeMapAccessor")
                .getMethod("aether_ii$getAttributes");
        private SetBindings() throws ReflectiveOperationException {}
    }
}
