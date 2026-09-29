package games.alejandrocoria.mismatchedboots.tooltip;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
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
import net.minecraft.world.item.component.TooltipProvider;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipPipelineCharacterizationTest {
    private static final String TRIM_TITLE = "item.minecraft.smithing_template.upgrade";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fullTooltipWrapsDetailPipelineWithTitle() {
        ItemStack stack = stack(Items.DISC_FRAGMENT_5);
        ItemStack before = stack.copy();

        List<Component> details = details(stack, TooltipFlag.NORMAL);
        List<Component> full = full(stack, TooltipFlag.NORMAL);

        assertEquals(stack.getStyledHoverName(), full.getFirst());
        assertEquals(details, full.subList(1, full.size()));
        assertEquals(1, details.size());
        assertEquals(((DiscFragmentItem) stack.getItem()).getDisplayName().withStyle(ChatFormatting.GRAY),
                details.getFirst());
        assertTrue(ItemStack.matches(before, stack));
    }

    @Test
    void knownComponentsKeepVanillaOrderAndStyles() {
        Holder<Enchantment> enchantment = enchantment("Source enchantment");
        ItemStack stack = boots(Items.LEATHER_BOOTS, 1.0);
        stack.set(DataComponents.TRIM, trim("Pattern", "Material"));
        stack.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 2));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Source lore"))));
        ItemStack before = stack.copy();

        List<Component> lines = details(stack, TooltipFlag.NORMAL);

        int trim = indexOfTranslation(lines, TRIM_TITLE);
        int enchant = lines.indexOf(Enchantment.getFullname(enchantment, 2));
        int dye = indexOfTranslation(lines, "item.dyed");
        int lore = indexOfText(lines, "Source lore");
        int attributes = indexOfTranslation(lines, "item.modifiers.feet");
        assertTrue(trim >= 0, "missing trim title: " + lines);
        assertTrue(enchant > trim, "enchantment did not follow trim: " + lines);
        assertTrue(dye > enchant, "dye did not follow enchantments: " + lines);
        assertTrue(lore > dye, "lore did not follow dye: " + lines);
        assertTrue(attributes > lore, "attributes did not follow lore: " + lines);
        assertTrue(ItemStack.matches(before, stack));
    }

    @Test
    void twoSourceTrimsRemainIndependentlyIdentifiable() {
        ItemStack left = boots(Items.IRON_BOOTS, 2.0);
        left.set(DataComponents.TRIM, trim("Left Pattern", "Left Material"));
        ItemStack right = boots(Items.GOLDEN_BOOTS, 1.0);
        right.set(DataComponents.TRIM, trim("Right Pattern", "Right Material"));
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();

        List<Component> leftLines = details(left, TooltipFlag.NORMAL);
        List<Component> rightLines = details(right, TooltipFlag.NORMAL);

        assertEquals(1, countTranslation(leftLines, TRIM_TITLE));
        assertEquals(1, countTranslation(rightLines, TRIM_TITLE));
        assertTrue(leftLines.stream().anyMatch(line -> line.getString().contains("Left Pattern")));
        assertTrue(leftLines.stream().anyMatch(line -> line.getString().contains("Left Material")));
        assertFalse(leftLines.stream().anyMatch(line -> line.getString().contains("Right Pattern")));
        assertTrue(rightLines.stream().anyMatch(line -> line.getString().contains("Right Pattern")));
        assertTrue(rightLines.stream().anyMatch(line -> line.getString().contains("Right Material")));
        assertFalse(rightLines.stream().anyMatch(line -> line.getString().contains("Left Pattern")));
        assertTrue(ItemStack.matches(leftBefore, left));
        assertTrue(ItemStack.matches(rightBefore, right));
    }

    @Test
    void normalAndAdvancedFlagsProduceDifferentStructuredDetails() {
        ItemStack stack = boots(Items.LEATHER_BOOTS, 1.0);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        stack.setDamageValue(7);
        ItemStack before = stack.copy();
        ItemStack undamaged = boots(Items.LEATHER_BOOTS, 1.0);
        ItemStack undamagedBefore = undamaged.copy();

        List<Component> normal = details(stack, TooltipFlag.NORMAL);
        List<Component> advanced = details(stack, TooltipFlag.ADVANCED);
        List<Component> undamagedNormal = details(undamaged, TooltipFlag.NORMAL);
        List<Component> undamagedAdvanced = details(undamaged, TooltipFlag.ADVANCED);

        assertTrue(hasTranslation(normal, "item.dyed"));
        assertFalse(hasTranslation(normal, "item.color"));
        assertFalse(hasTranslation(normal, "item.durability"));
        assertFalse(hasTranslation(advanced, "item.dyed"));
        assertTrue(hasTranslation(advanced, "item.color"));
        assertTrue(hasTranslation(advanced, "item.durability"));
        assertTrue(hasTranslation(advanced, "item.components"));
        assertTrue(advanced.contains(
                Component.literal("minecraft:leather_boots").withStyle(ChatFormatting.DARK_GRAY)));
        assertFalse(hasTranslation(undamagedNormal, "item.durability"));
        assertFalse(hasTranslation(undamagedAdvanced, "item.durability"));
        assertTrue(ItemStack.matches(before, stack));
        assertTrue(ItemStack.matches(undamagedBefore, undamaged));
    }

    @Test
    void componentHidingIsStructuralButHideTooltipMustBeCheckedByCaller() {
        ItemStack boots = boots(Items.LEATHER_BOOTS, 1.0);
        boots.set(DataComponents.TRIM, trim("Hidden Pattern", "Hidden Material"));
        boots.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Visible lore"))));
        TooltipDisplay trimHidden = TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true);
        boots.set(DataComponents.TOOLTIP_DISPLAY, trimHidden);
        ItemStack bootsBefore = boots.copy();

        List<Component> hiddenTrimLines = details(boots, TooltipFlag.NORMAL);

        assertFalse(hasTranslation(hiddenTrimLines, TRIM_TITLE));
        assertTrue(hiddenTrimLines.stream().anyMatch(line -> line.getString().equals("Visible lore")));
        assertTrue(ItemStack.matches(bootsBefore, boots));

        ItemStack hidden = stack(Items.DISC_FRAGMENT_5);
        TooltipDisplay hideAll = new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents());
        hidden.set(DataComponents.TOOLTIP_DISPLAY, hideAll);
        ItemStack hiddenBefore = hidden.copy();

        assertTrue(full(hidden, TooltipFlag.NORMAL).isEmpty());
        assertFalse(details(hidden, TooltipFlag.NORMAL).isEmpty(),
                "addDetailsToTooltip unexpectedly enforced hide_tooltip");
        assertTrue(ItemStack.matches(hiddenBefore, hidden));
    }

    @Test
    void customAndNaturalNamesAreAvailableWithoutEnteringDetails() {
        ItemStack stack = boots(Items.IRON_BOOTS, 2.0);
        Component customName = Component.literal("Named source");
        stack.set(DataComponents.CUSTOM_NAME, customName);
        ItemStack before = stack.copy();

        assertEquals(customName, stack.getCustomName());
        assertEquals(customName, stack.getHoverName());
        assertEquals(Component.translatable(stack.getItem().getDescriptionId()), stack.getItemName());
        assertEquals(customName.getString(), full(stack, TooltipFlag.NORMAL).getFirst().getString());
        assertFalse(details(stack, TooltipFlag.NORMAL).stream()
                .anyMatch(line -> line.getString().equals(customName.getString())));
        assertTrue(ItemStack.matches(before, stack));
    }

    @Test
    void sourceAndRootGameplayDetailsCanBeGeneratedSeparately() {
        Holder<Enchantment> enchantment = enchantment("Independent enchantment");
        ItemStack source = boots(Items.IRON_BOOTS, 2.0);
        source.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 1));
        ItemStack root = boots(Items.GOLDEN_BOOTS, 1.0);
        root.set(DataComponents.ENCHANTMENTS, enchantments(enchantment, 2));
        ItemStack sourceBefore = source.copy();
        ItemStack rootBefore = root.copy();

        List<Component> sourceLines = details(source, TooltipFlag.NORMAL);
        List<Component> rootLines = details(root, TooltipFlag.NORMAL);

        assertTrue(sourceLines.contains(Enchantment.getFullname(enchantment, 1)));
        assertFalse(sourceLines.contains(Enchantment.getFullname(enchantment, 2)));
        assertTrue(rootLines.contains(Enchantment.getFullname(enchantment, 2)));
        assertFalse(rootLines.contains(Enchantment.getFullname(enchantment, 1)));

        Component sourceArmor = onlyArmorModifier(sourceLines);
        Component rootArmor = onlyArmorModifier(rootLines);
        assertNotEquals(sourceArmor, rootArmor);
        assertTrue(ItemStack.matches(sourceBefore, source));
        assertTrue(ItemStack.matches(rootBefore, root));
    }

    @Test
    void arbitraryTooltipProviderComponentsAreNotDiscoveredGenerically() {
        AtomicInteger calls = new AtomicInteger();
        ProbeTooltipProvider probe = new ProbeTooltipProvider(calls);
        DataComponentType<ProbeTooltipProvider> probeType =
                DataComponentType.<ProbeTooltipProvider>builder()
                        .persistent(Codec.STRING.xmap(value -> probe, value -> "probe"))
                        .build();
        ItemStack control = boots(Items.IRON_BOOTS, 2.0);
        ItemStack withProbe = control.copy();
        withProbe.set(probeType, probe);
        ItemStack before = withProbe.copy();

        assertEquals(details(control, TooltipFlag.NORMAL), details(withProbe, TooltipFlag.NORMAL));
        assertEquals(0, calls.get());
        assertTrue(ItemStack.matches(before, withProbe));
    }

    private static List<Component> full(ItemStack stack, TooltipFlag flag) {
        return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, flag);
    }

    private static List<Component> details(ItemStack stack, TooltipFlag flag) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Component> lines = new ArrayList<>();
        stack.addDetailsToTooltip(Item.TooltipContext.EMPTY, display, null, flag, lines::add);
        return List.copyOf(lines);
    }

    private static ItemStack stack(Item item) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                .set(DataComponents.ITEM_MODEL, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))
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
                .set(DataComponents.ITEM_MODEL, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))
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

    private static Component onlyArmorModifier(List<Component> lines) {
        String armorTranslation = Attributes.ARMOR.value().getDescriptionId();
        List<Component> modifiers = lines.stream()
                .filter(line -> line.getContents() instanceof TranslatableContents contents
                        && contents.getKey().startsWith("attribute.modifier."))
                .filter(line -> containsTranslation(line, armorTranslation))
                .toList();
        assertEquals(1, modifiers.size(), "unexpected armor modifier lines: " + lines);
        return modifiers.getFirst();
    }

    private static int indexOfTranslation(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (hasTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static int countTranslation(List<Component> lines, String key) {
        return (int) lines.stream().filter(line -> hasTranslation(line, key)).count();
    }

    private static int indexOfText(List<Component> lines, String text) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).getString().equals(text)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasTranslation(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> hasTranslation(line, key));
    }

    private static boolean hasTranslation(Component component, String key) {
        return component.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key);
    }

    private static boolean containsTranslation(Component component, String key) {
        if (hasTranslation(component, key)) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsTranslation(sibling, key));
    }

    private record ProbeTooltipProvider(AtomicInteger calls) implements TooltipProvider {
        @Override
        public void addToTooltip(
                Item.TooltipContext context,
                Consumer<Component> consumer,
                TooltipFlag flag,
                DataComponentGetter components) {
            calls.incrementAndGet();
            consumer.accept(Component.literal("Unexpected generic component tooltip"));
        }
    }
}
