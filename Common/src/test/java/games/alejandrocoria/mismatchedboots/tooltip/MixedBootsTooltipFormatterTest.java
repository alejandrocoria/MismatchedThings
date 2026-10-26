package games.alejandrocoria.mismatchedboots.tooltip;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsTooltipFormatterTest {
    private static final String LEFT_KEY = "tooltip.mismatchedboots.mixed_boots.left";
    private static final String RIGHT_KEY = "tooltip.mismatchedboots.mixed_boots.right";
    private static final String NAME_WITH_NATURAL_KEY =
            "tooltip.mismatchedboots.mixed_boots.name_with_natural";
    private static final String TRIM_TITLE = "item.minecraft.smithing_template.upgrade";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plainSourcesProduceOrderedIdentitySectionsAndSeparators() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);

        List<Component> lines = format(left, right);

        assertEquals(4, lines.size());
        assertSide(lines.get(0), LEFT_KEY, left.getHoverName());
        assertInstanceOf(TooltipSeparator.class, lines.get(1));
        assertEquals("", lines.get(1).getString());
        assertEquals("", lines.get(1).copy().getString());
        assertSide(lines.get(2), RIGHT_KEY, right.getHoverName());
        assertInstanceOf(TooltipSeparator.class, lines.get(3));
    }

    @Test
    void twoTrimsRetainTheirOwnStructuredLinesAndStyles() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        right.set(DataComponents.TRIM, trim("Right Pattern", "Right Material"));

        List<Component> lines = format(left, right);
        int firstSeparator = lines.indexOf(TooltipSeparator.INSTANCE);
        int secondSeparator = lines.lastIndexOf(TooltipSeparator.INSTANCE);

        assertEquals(componentDetails(left, DataComponents.TRIM), lines.subList(1, firstSeparator));
        assertEquals(componentDetails(right, DataComponents.TRIM),
                     lines.subList(firstSeparator + 2, secondSeparator));
        assertEquals(2, countTranslation(lines, TRIM_TITLE));
        assertTrue(indexContaining(lines, "Left Pattern") < indexContaining(lines, "Right Pattern"));
    }

    @Test
    void ordinarySourceBlankLinesAreNotStructuralSeparators() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Before blank"), CommonComponents.EMPTY, Component.literal("After blank"))));
        List<Component> lines = format(left, boots(Items.IRON_BOOTS, 2.0));

        int before = indexContaining(lines, "Before blank");
        int after = indexContaining(lines, "After blank");
        assertEquals(before + 2, after);
        assertEquals("", lines.get(before + 1).getString());
        assertFalse(lines.get(before + 1) instanceof TooltipSeparator);
        assertEquals(2, lines.stream().filter(TooltipSeparator.class::isInstance).count());
        assertTrue(after < lines.indexOf(TooltipSeparator.INSTANCE));
    }

    @Test
    void visualAndItemProvidedDetailsRemainInTheirSourceRegions() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699));
        Component lore = Component.literal("Source lore");
        left.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        ItemStack right = feetItem(Items.DISC_FRAGMENT_5);

        List<Component> lines = format(left, right);

        assertTrue(hasTranslation(lines, "item.dyed"));
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals(lore.getString())));
        assertTrue(lines.contains(((DiscFragmentItem) right.getItem()).getDisplayName()
                                                                      .withStyle(ChatFormatting.GRAY)));
    }

    @Test
    void knownGameplayDetailsAreSuppressedStructurally() {
        Holder<Enchantment> enchantment = enchantment("Source enchantment");
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 2));
        left.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        left.setDamageValue(7);

        List<Component> lines = format(left, boots(Items.IRON_BOOTS, 2.0));

        assertFalse(lines.contains(Enchantment.getFullname(enchantment, 2)));
        assertFalse(hasTranslation(lines, "item.modifiers.feet"));
        assertFalse(hasTranslation(lines, "item.unbreakable"));
        assertFalse(hasTranslation(lines, "item.durability"));
    }

    @Test
    void formatterComposesWithTheSourceDisplayWithoutMutatingIt() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.TRIM, trim("Hidden Pattern", "Hidden Material"));
        Component lore = Component.literal("Visible lore");
        left.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        TooltipDisplay display = TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true);
        left.set(DataComponents.TOOLTIP_DISPLAY, display);
        ItemStack leftBefore = left.copy();

        List<Component> lines = format(left, boots(Items.IRON_BOOTS, 2.0));

        assertFalse(hasTranslation(lines, TRIM_TITLE));
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals(lore.getString())));
        assertEquals(display, left.get(DataComponents.TOOLTIP_DISPLAY));
        assertTrue(ItemStack.matches(leftBefore, left));
    }

    @Test
    void hiddenSourceRetainsOnlyItsIdentity() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Hidden Pattern", "Hidden Material"));
        left.set(DataComponents.TOOLTIP_DISPLAY,
                 new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));

        for (TooltipFlag flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            List<Component> lines = format(left, boots(Items.GOLDEN_BOOTS, 1.0), flag);

            assertSide(lines.get(0), LEFT_KEY, left.getHoverName());
            assertInstanceOf(TooltipSeparator.class, lines.get(1));
            assertFalse(hasTranslation(lines, TRIM_TITLE));
        }
    }

    @Test
    void sourceAdaptationIsScopedToVisibleDetailsAndPreservesIdentityAndProvenance() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Left detail"))));
        ItemStack right = boots(Items.IRON_BOOTS, 2.0);
        right.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Right detail"))));
        MixedBootsData data = data(left, right);
        List<Component> lines = new ArrayList<>();
        MixedBootsTooltipFormatter.appendSourceSections(data, Item.TooltipContext.EMPTY, TooltipFlag.NORMAL,
                                                        lines::add, (source, output) -> line -> {
                    if (source.is(Items.LEATHER_BOOTS)) {
                        output.accept(Component.literal("Adapted detail"));
                    } else {
                        output.accept(line);
                    }
                });
        assertSide(lines.getFirst(), LEFT_KEY, left.getHoverName());
        assertTrue(lines.contains(Component.literal("Adapted detail")));
        assertFalse(lines.stream().anyMatch(line -> line.getString().equals("Left detail")));
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals("Right detail")));
        assertEquals(data(left, right), data);

        left.set(DataComponents.TOOLTIP_DISPLAY,
                 new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        lines.clear();
        MixedBootsTooltipFormatter.appendSourceSections(data(left, right), Item.TooltipContext.EMPTY,
                                                        TooltipFlag.NORMAL, lines::add, (source, output) -> {
                    assertFalse(source.is(Items.LEATHER_BOOTS), "Hidden source reached the detail adapter");
                    return output;
                });
        assertSide(lines.getFirst(), LEFT_KEY, left.getHoverName());
        assertFalse(lines.contains(Component.literal("Adapted detail")));
    }

    @Test
    void sourceBlockCanBeAppendedBeforeDetailsEvenWhenBothSourcesHaveNoOrdinaryLines() {
        ItemStack left = boots(Items.IRON_BOOTS, 2);
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1);

        for (TooltipFlag flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            List<Component> lines = new ArrayList<>();
            MixedBootsTooltipFormatter.appendSourceSections(data(left, right), Item.TooltipContext.EMPTY, flag,
                                                            lines::add, (source, output) -> {
                        output.accept(Component.literal(source.is(Items.IRON_BOOTS) ? "Left block" : "Right block"));
                        return output;
                    });

            int idLines = flag.isAdvanced() ? 1 : 0;
            assertEquals(6 + 2 * idLines, lines.size());
            assertSide(lines.get(0), LEFT_KEY, left.getHoverName());
            assertEquals(Component.literal("Left block"), lines.get(1 + idLines));
            assertInstanceOf(TooltipSeparator.class, lines.get(2 + idLines));
            assertSide(lines.get(3 + idLines), RIGHT_KEY, right.getHoverName());
            assertEquals(Component.literal("Right block"), lines.get(4 + 2 * idLines));
            assertInstanceOf(TooltipSeparator.class, lines.get(5 + 2 * idLines));
            if (flag.isAdvanced()) {
                assertEquals(Component.literal("minecraft:iron_boots").withStyle(ChatFormatting.DARK_GRAY),
                             lines.get(1));
                assertEquals(Component.literal("minecraft:golden_boots").withStyle(ChatFormatting.DARK_GRAY),
                             lines.get(5));
            }
        }
    }

    @Test
    void customSourceIdentityIncludesTheNaturalNameStructurally() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        Component customName = Component.literal("Traveler's boots");
        left.set(DataComponents.CUSTOM_NAME, customName);

        List<Component> lines = format(left, boots(Items.GOLDEN_BOOTS, 1.0));
        Component name = sideName(lines.getFirst());
        TranslatableContents combined = translation(name, NAME_WITH_NATURAL_KEY);

        assertEquals(customName, combined.getArgs()[0]);
        Component naturalName = assertInstanceOf(Component.class, combined.getArgs()[1]);
        assertEquals(left.getItemName().getContents(), naturalName.getContents());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), naturalName.getStyle().getColor());
    }

    @Test
    void structurallyEqualCustomAndNaturalNamesAreNotDuplicated() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.CUSTOM_NAME, left.getItemName());

        List<Component> lines = format(left, boots(Items.GOLDEN_BOOTS, 1.0));

        assertEquals(left.getHoverName(), sideName(lines.getFirst()));
        assertFalse(hasTranslation(lines.getFirst(), NAME_WITH_NATURAL_KEY));
    }

    @Test
    void advancedTooltipPlacesSourceIdsBelowNamesWithoutDuplicatingNativeDiagnostics() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        left.setDamageValue(7);
        ItemStack right = boots(Items.IRON_BOOTS, 2.0);
        right.set(DataComponents.CUSTOM_NAME, Component.literal("Traveler's boots"));
        ItemStack root = boots(Items.DIAMOND_BOOTS, 3.0);
        root.setDamageValue(11);

        List<Component> lines = new ArrayList<>(format(left, right, TooltipFlag.ADVANCED));
        addDetails(root, TooltipFlag.ADVANCED, lines);

        assertFalse(hasTranslation(lines, "item.dyed"));
        assertTrue(hasTranslation(lines, "item.color"));
        assertEquals(1, countTranslation(lines, "item.durability"));
        assertEquals(1, countTranslation(lines, "item.components"));
        Component leftId = Component.literal("minecraft:leather_boots").withStyle(ChatFormatting.DARK_GRAY);
        Component rightId = Component.literal("minecraft:iron_boots").withStyle(ChatFormatting.DARK_GRAY);
        assertEquals(leftId, lines.get(1));
        assertEquals(rightId, lines.get(lines.indexOf(TooltipSeparator.INSTANCE) + 2));
        assertEquals(1, lines.stream().filter(leftId::equals).count());
        assertEquals(1, lines.stream().filter(rightId::equals).count());
        List<Component> normal = format(left, right);
        assertFalse(normal.contains(leftId));
        assertFalse(normal.contains(rightId));
        assertTrue(lines.contains(
                Component.literal("minecraft:diamond_boots").withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Test
    void inverseInputsExchangeCompleteSourceRegions() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        right.set(DataComponents.TRIM, trim("Right Pattern", "Right Material"));

        List<Component> primary = format(left, right);
        List<Component> inverse = format(right, left);

        assertTrue(indexContaining(primary, "Left Pattern") < indexContaining(primary, "Right Pattern"));
        assertTrue(indexContaining(inverse, "Right Pattern") < indexContaining(inverse, "Left Pattern"));
        assertSide(primary.getFirst(), LEFT_KEY, left.getHoverName());
        assertSide(inverse.getFirst(), LEFT_KEY, right.getHoverName());
    }

    @Test
    void formatterDoesNotMutateInputsOrStoredSnapshots() {
        ItemStack left = boots(Items.LEATHER_BOOTS, 1.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        MixedBootsData data = data(left, right);
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        ItemStack storedLeftBefore = data.left().source();
        ItemStack storedRightBefore = data.right().source();
        List<Component> lines = new ArrayList<>();

        MixedBootsTooltipFormatter.appendSourceSections(
                data, Item.TooltipContext.EMPTY, TooltipFlag.NORMAL, lines::add);

        assertTrue(ItemStack.matches(leftBefore, left));
        assertTrue(ItemStack.matches(rightBefore, right));
        assertTrue(ItemStack.matches(storedLeftBefore, data.left().source()));
        assertTrue(ItemStack.matches(storedRightBefore, data.right().source()));
    }

    private static List<Component> format(ItemStack left, ItemStack right) {
        return format(left, right, TooltipFlag.NORMAL);
    }

    private static List<Component> format(ItemStack left, ItemStack right, TooltipFlag flag) {
        List<Component> lines = new ArrayList<>();
        MixedBootsTooltipFormatter.appendSourceSections(
                data(left, right), Item.TooltipContext.EMPTY, flag, lines::add);
        return List.copyOf(lines);
    }

    private static MixedBootsData data(ItemStack left, ItemStack right) {
        return new MixedBootsData(new BootHalf(left), new BootHalf(right));
    }

    private static <T extends net.minecraft.world.item.component.TooltipProvider> List<Component> componentDetails(
            ItemStack stack, net.minecraft.core.component.DataComponentType<T> type) {
        List<Component> lines = new ArrayList<>();
        stack.addToTooltip(
                type,
                Item.TooltipContext.EMPTY,
                stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT),
                lines::add,
                TooltipFlag.NORMAL);
        return List.copyOf(lines);
    }

    @SuppressWarnings("deprecation")
    private static void addDetails(ItemStack stack, TooltipFlag flag, List<Component> lines) {
        stack.addDetailsToTooltip(
                Item.TooltipContext.EMPTY,
                stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT),
                null,
                flag,
                lines::add);
    }

    private static ItemStack feetItem(Item item) {
        DataComponentMap components = DataComponentMap.builder()
                                                      .set(DataComponents.MAX_STACK_SIZE, 1)
                                                      .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                                                      .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                                                      .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
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

    private static void assertSide(Component line, String key, Component expectedName) {
        assertEquals(3, line.getSiblings().size());
        Component label = line.getSiblings().get(0);
        TranslatableContents contents = translation(label, key);
        assertEquals(0, contents.getArgs().length);
        assertTrue(label.getStyle().isBold());
        assertEquals(CommonComponents.SPACE, line.getSiblings().get(1));
        assertEquals(expectedName, sideName(line));
        assertFalse(sideName(line).getStyle().isBold());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), line.getStyle().getColor());
    }

    private static Component sideName(Component line) {
        return line.getSiblings().get(2);
    }

    private static TranslatableContents translation(Component component, String key) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals(key, contents.getKey());
        return contents;
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

}
