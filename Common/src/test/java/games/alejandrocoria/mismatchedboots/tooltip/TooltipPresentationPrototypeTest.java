package games.alejandrocoria.mismatchedboots.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiscFragmentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipPresentationPrototypeTest {
    private static final String TRIM_TITLE = "item.minecraft.smithing_template.upgrade";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nestedCollectionPreservesVisualDetailsWithoutGameplayDuplicates() {
        Holder<Enchantment> enchantment = enchantment("Source enchantment");
        ItemStack source = boots(Items.LEATHER_BOOTS, 1.0);
        source.set(DataComponents.TRIM, trim("Source Pattern", "Source Material"));
        source.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 2));
        source.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699));
        source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Source lore"))));
        source.setDamageValue(7);
        ItemStack before = source.copy();

        List<Component> lines = nestedDetails(source, TooltipFlag.ADVANCED, candidateGameplaySuppressions());

        assertTrue(hasTranslation(lines, TRIM_TITLE));
        assertTrue(hasTranslation(lines, "item.color"));
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals("Source lore")));
        assertFalse(lines.contains(Enchantment.getFullname(enchantment, 2)));
        assertFalse(hasTranslation(lines, "item.modifiers.feet"));
        assertFalse(hasTranslation(lines, "item.durability"));
        assertTrue(lines.contains(Component.literal("minecraft:leather_boots").withStyle(ChatFormatting.DARK_GRAY)),
                "advanced item diagnostics are not governed by a component visibility flag");
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void nestedCollectionComposesWithTheSourceDisplay() {
        ItemStack source = boots(Items.LEATHER_BOOTS, 1.0);
        source.set(DataComponents.TRIM, trim("Hidden Pattern", "Hidden Material"));
        source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Visible lore"))));
        source.set(DataComponents.TOOLTIP_DISPLAY,
                TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        ItemStack before = source.copy();

        List<Component> lines = nestedDetails(source, TooltipFlag.NORMAL, candidateGameplaySuppressions());

        assertFalse(hasTranslation(lines, TRIM_TITLE));
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals("Visible lore")));
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void hiddenSourceStillHasAnIdentityButNoNestedDetails() {
        ItemStack source = boots(Items.IRON_BOOTS, 2.0);
        source.set(DataComponents.TRIM, trim("Hidden Pattern", "Hidden Material"));
        source.set(DataComponents.TOOLTIP_DISPLAY,
                new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        ItemStack before = source.copy();

        List<Component> section = sourceSection("Left", source);

        assertEquals(List.of(Component.literal("Left: ").append(source.getHoverName())
                .withStyle(ChatFormatting.GRAY)), section);
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void customSourceIdentityAlsoShowsItsNaturalItemName() {
        ItemStack source = boots(Items.IRON_BOOTS, 2.0);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Traveler's boots"));
        ItemStack before = source.copy();

        Component identity = sourceIdentity("Left", source);

        MutableComponent expected = Component.literal("Left: ")
                .append(source.getHoverName())
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(source.getItemName().copy().withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY);
        assertEquals(expected, identity);
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void structurallyEqualCustomAndNaturalNamesAreNotDuplicated() {
        ItemStack source = boots(Items.IRON_BOOTS, 2.0);
        source.set(DataComponents.CUSTOM_NAME, source.getItemName());
        ItemStack before = source.copy();

        Component identity = sourceIdentity("Left", source);

        assertEquals(Component.literal("Left: ").append(source.getHoverName())
                .withStyle(ChatFormatting.GRAY), identity);
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void localAssemblyKeepsSourceRegionsBeforeTheActualRootDetails() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        right.set(DataComponents.TRIM, trim("Right Pattern", "Right Material"));
        ItemStack root = boots(Items.DIAMOND_BOOTS, 3.0);
        root.set(DataComponents.TRIM, trim("Configured Root Pattern", "Configured Root Material"));
        root.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Live root lore"))));
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        ItemStack rootBefore = root.copy();

        List<Component> lines = assembledDetails(left, right, root, TooltipFlag.NORMAL);

        int leftLabel = lines.indexOf(Component.literal("Left: ").append(left.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        int leftTrim = indexContaining(lines, "Left Pattern");
        int rightLabel = lines.indexOf(Component.literal("Right: ").append(right.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        int rightTrim = indexContaining(lines, "Right Pattern");
        int rootTrimHeader = lastIndexOfTranslation(lines, TRIM_TITLE);
        int rootTrim = indexContaining(lines, "Configured Root Pattern");
        int rootLore = indexContaining(lines, "Live root lore");
        assertTrue(leftLabel >= 0 && leftTrim > leftLabel);
        assertTrue(rightLabel > leftTrim && rightTrim > rightLabel);
        assertTrue(rootTrim > rightTrim && rootLore > rootTrim);
        assertEquals(CommonComponents.EMPTY, lines.get(rightLabel - 1));
        assertEquals(CommonComponents.EMPTY, lines.get(rootTrimHeader - 1));
        assertTrue(ItemStack.matches(leftBefore, left));
        assertTrue(ItemStack.matches(rightBefore, right));
        assertTrue(ItemStack.matches(rootBefore, root));
    }

    @Test
    void nestedCollectionPreservesItemProvidedDetails() {
        ItemStack source = stack(Items.DISC_FRAGMENT_5);
        ItemStack before = source.copy();

        List<Component> nested = nestedDetails(source, TooltipFlag.NORMAL, Set.of());

        assertEquals(List.of(((DiscFragmentItem) source.getItem()).getDisplayName()
                .withStyle(ChatFormatting.GRAY)), nested);
        assertTrue(ItemStack.matches(before, source));
    }

    @Test
    void advancedModeAppliesOnlyToTheLiveRootRegion() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        left.setDamageValue(7);
        ItemStack right = boots(Items.IRON_BOOTS, 2.0);
        ItemStack root = boots(Items.DIAMOND_BOOTS, 3.0);
        root.setDamageValue(11);

        List<Component> lines = assembledDetails(left, right, root, TooltipFlag.ADVANCED);

        assertTrue(hasTranslation(lines, "item.dyed"));
        assertFalse(hasTranslation(lines, "item.color"));
        assertEquals(1, countTranslation(lines, "item.durability"));
        assertFalse(lines.contains(
                Component.literal("minecraft:leather_boots").withStyle(ChatFormatting.DARK_GRAY)));
        assertTrue(lines.contains(
                Component.literal("minecraft:diamond_boots").withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Test
    void inverseAssemblySwapsOnlyTheSourceRegions() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        right.set(DataComponents.TRIM, trim("Right Pattern", "Right Material"));
        ItemStack root = boots(Items.DIAMOND_BOOTS, 3.0);

        List<Component> primary = assembledDetails(left, right, root, TooltipFlag.NORMAL);
        List<Component> inverse = assembledDetails(right, left, root, TooltipFlag.NORMAL);

        assertTrue(indexContaining(primary, "Left Pattern") < indexContaining(primary, "Right Pattern"));
        assertTrue(indexContaining(inverse, "Right Pattern") < indexContaining(inverse, "Left Pattern"));
        assertEquals(nestedDetails(root, TooltipFlag.NORMAL, Set.of()),
                primary.subList(primary.size() - nestedDetails(root, TooltipFlag.NORMAL, Set.of()).size(),
                        primary.size()));
        assertEquals(nestedDetails(root, TooltipFlag.NORMAL, Set.of()),
                inverse.subList(inverse.size() - nestedDetails(root, TooltipFlag.NORMAL, Set.of()).size(),
                        inverse.size()));
    }

    @Test
    void explicitSelectionDropsContributionsOutsideItsMaintainedList() {
        Holder<Enchantment> enchantment = enchantment("Unselected enchantment");
        ItemStack source = boots(Items.LEATHER_BOOTS, 1.0);
        source.set(DataComponents.TRIM, trim("Selected Pattern", "Selected Material"));
        source.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 1));
        source.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        ItemStack before = source.copy();

        List<Component> complete = nestedDetails(source, TooltipFlag.NORMAL, Set.of());
        List<Component> selected = explicitlySelectedDetails(source, TooltipFlag.NORMAL);

        assertTrue(hasTranslation(complete, TRIM_TITLE));
        assertTrue(complete.contains(Enchantment.getFullname(enchantment, 1)));
        assertTrue(hasTranslation(complete, "item.unbreakable"));
        assertTrue(hasTranslation(selected, TRIM_TITLE));
        assertFalse(selected.contains(Enchantment.getFullname(enchantment, 1)));
        assertFalse(hasTranslation(selected, "item.unbreakable"));
        assertTrue(ItemStack.matches(before, source));
    }

    private static List<Component> assembledDetails(
            ItemStack left, ItemStack right, ItemStack root, TooltipFlag flag) {
        List<Component> lines = new ArrayList<>();
        lines.addAll(sourceSection("Left", left));
        lines.add(CommonComponents.EMPTY);
        lines.addAll(sourceSection("Right", right));
        lines.add(CommonComponents.EMPTY);
        lines.addAll(nestedDetails(root, flag, Set.of()));
        return List.copyOf(lines);
    }

    private static List<Component> sourceSection(String label, ItemStack source) {
        List<Component> lines = new ArrayList<>();
        lines.add(sourceIdentity(label, source));
        TooltipDisplay display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (!display.hideTooltip()) {
            lines.addAll(nestedDetails(source, TooltipFlag.NORMAL, candidateGameplaySuppressions()));
        }
        return List.copyOf(lines);
    }

    private static Component sourceIdentity(String label, ItemStack source) {
        MutableComponent identity = Component.literal(label + ": ").append(source.getHoverName());
        Component customName = source.getCustomName();
        Component naturalName = source.getItemName();
        if (customName != null && !customName.equals(naturalName)) {
            identity.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
            identity.append(naturalName.copy().withStyle(ChatFormatting.DARK_GRAY));
            identity.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }
        return identity.withStyle(ChatFormatting.GRAY);
    }

    private static List<Component> nestedDetails(
            ItemStack stack, TooltipFlag flag, Set<DataComponentType<?>> hidden) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        for (DataComponentType<?> type : hidden) {
            display = display.withHidden(type, true);
        }
        List<Component> lines = new ArrayList<>();
        stack.addDetailsToTooltip(Item.TooltipContext.EMPTY, display, null, flag, lines::add);
        return List.copyOf(lines);
    }

    private static Set<DataComponentType<?>> candidateGameplaySuppressions() {
        return Set.of(
                DataComponents.DAMAGE,
                DataComponents.MAX_DAMAGE,
                DataComponents.REPAIR_COST,
                DataComponents.REPAIRABLE,
                DataComponents.ATTRIBUTE_MODIFIERS,
                DataComponents.ENCHANTMENTS,
                DataComponents.STORED_ENCHANTMENTS,
                DataComponents.EQUIPPABLE,
                DataComponents.DAMAGE_RESISTANT,
                DataComponents.UNBREAKABLE,
                DataComponents.GLIDER,
                DataComponents.ENCHANTABLE,
                DataComponents.RARITY,
                DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    private static List<Component> explicitlySelectedDetails(ItemStack stack, TooltipFlag flag) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, display, lines::add, flag);
        stack.addToTooltip(DataComponents.TRIM, Item.TooltipContext.EMPTY, display, lines::add, flag);
        stack.addToTooltip(DataComponents.DYED_COLOR, Item.TooltipContext.EMPTY, display, lines::add, flag);
        stack.addToTooltip(DataComponents.LORE, Item.TooltipContext.EMPTY, display, lines::add, flag);
        return List.copyOf(lines);
    }

    private static ItemStack stack(Item item) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }

    private static ItemStack boots(Item item, double armor) {
        ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(
                        Identifier.fromNamespaceAndPath("mismatchedboots_test", "armor"),
                        armor,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
                .build();
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.MAX_DAMAGE, 100)
                .set(DataComponents.DAMAGE, 0)
                .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .set(DataComponents.ATTRIBUTE_MODIFIERS, attributes)
                .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }

    private static Holder<Enchantment> enchantment(String name) {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(Holder.direct(Items.IRON_BOOTS)),
                1,
                3,
                Enchantment.constantCost(1),
                Enchantment.constantCost(1),
                1,
                EquipmentSlotGroup.FEET);
        return Holder.direct(new Enchantment(
                Component.literal(name),
                definition,
                HolderSet.direct(List.of()),
                DataComponentMap.EMPTY));
    }

    private static ItemEnchantments enchantments(Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        return enchantments.toImmutable();
    }

    private static ArmorTrim trim(String patternName, String materialName) {
        Holder<TrimMaterial> material = Holder.direct(new TrimMaterial(
                MaterialAssetGroup.QUARTZ, Component.literal(materialName)));
        Holder<TrimPattern> pattern = Holder.direct(new TrimPattern(
                Identifier.fromNamespaceAndPath("mismatchedboots_test", patternName.toLowerCase().replace(' ', '_')),
                Component.literal(patternName),
                false));
        return new ArmorTrim(material, pattern);
    }

    private static boolean hasTranslation(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> hasTranslation(line, key));
    }

    private static long countTranslation(List<Component> lines, String key) {
        return lines.stream().filter(line -> hasTranslation(line, key)).count();
    }

    private static boolean hasTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && hasTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> hasTranslation(sibling, key));
    }

    private static int indexContaining(List<Component> lines, String text) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).getString().contains(text)) {
                return index;
            }
        }
        return -1;
    }

    private static int lastIndexOfTranslation(List<Component> lines, String key) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (hasTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }
}
