package games.alejandrocoria.mismatchedboots.compatibility.rei;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MismatchedWorkbenchReiExamplesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void candidatesAreValidCountOneExactUniqueAndStablySorted() {
        ItemStack secondIronVariant = named(boots(Items.IRON_BOOTS), "second iron");
        ItemStack firstIronVariant = named(boots(Items.IRON_BOOTS), "first iron");
        ItemStack duplicateFirstIron = firstIronVariant.copyWithCount(8);

        List<ItemStack> candidates = MismatchedWorkbenchReiExamples.collectCandidates(List.of(
                secondIronVariant,
                boots(Items.DIAMOND_BOOTS),
                equippable(Items.IRON_HELMET, EquipmentSlot.HEAD),
                firstIronVariant,
                duplicateFirstIron));

        assertEquals(List.of(Items.DIAMOND_BOOTS, Items.IRON_BOOTS, Items.IRON_BOOTS),
                candidates.stream().map(ItemStack::getItem).toList());
        assertEquals(List.of("second iron", "first iron"),
                candidates.subList(1, 3).stream().map(stack -> stack.getHoverName().getString()).toList());
        assertTrue(candidates.stream().allMatch(stack -> stack.getCount() == 1));
    }

    @Test
    void categoryAndRelevantItemSearchesRequestOneInformationalDisplay() {
        ItemStack mixed = mixedBoots();
        ItemStack iron = boots(Items.IRON_BOOTS);
        ItemStack gold = boots(Items.GOLDEN_BOOTS);
        ItemStack station = stack(Items.CRAFTING_TABLE);

        assertTrue(requestsDisplay(true, List.of(), List.of()));
        assertTrue(requestsDisplay(false, List.of(mixed), List.of()));
        assertTrue(requestsDisplay(false, List.of(), List.of(iron)));
        assertTrue(requestsDisplay(false, List.of(), List.of(station)));
        assertTrue(requestsDisplay(true, List.of(mixed, mixed.copy()), List.of(iron, gold, station)));
    }

    @Test
    void unrelatedSearchesStayEmptyRegardlessOfPreviouslySelectedCategory() {
        // A preferred tab is intentionally absent from the request inputs: it adds no recipes.
        assertFalse(requestsDisplay(false, List.of(), List.of()));
        assertFalse(requestsDisplay(false, List.of(boots(Items.IRON_BOOTS)), List.of()));
        assertFalse(requestsDisplay(false, List.of(stack(Items.PAPER)), List.of(stack(Items.STONE))));
        assertFalse(requestsDisplay(false, List.of(), List.of(equippable(Items.IRON_HELMET, EquipmentSlot.HEAD))));
        assertFalse(requestsDisplay(false, List.of(), List.of(mixedBoots())));
    }

    @Test
    void generatorHasOnlyOneProducingCallbackAndDoesNotReadPreferredCategory() throws IOException {
        // REI 26.1.819 calls recipe/usage callbacks AND generate, retaining distinct objects.
        ClassNode generator = new ClassNode();
        try (var bytecode = getClass().getResourceAsStream("MismatchedBootsReiClientPlugin$1.class")) {
            assertNotNull(bytecode);
            new ClassReader(bytecode).accept(generator, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(generator.methods.stream().anyMatch(method -> method.name.equals("generate")));
        assertFalse(generator.methods.stream().anyMatch(method -> method.name.equals("getRecipeFor")
                || method.name.equals("getUsageFor")));
        for (var method : generator.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    assertFalse(call.name.equals("getPreferredOpenedCategory"));
                }
            }
        }
    }

    private static boolean requestsDisplay(boolean category, List<ItemStack> recipes, List<ItemStack> usages) {
        return MismatchedWorkbenchReiExamples.requestsDisplay(category, recipes, usages, Items.CRAFTING_TABLE);
    }

    private static ItemStack mixedBoots() {
        ItemStack mixed = boots(Items.DIAMOND_BOOTS);
        mixed.set(ModContent.createMixedBootsDataType(), new MixedBootsData(
                new BootHalf(boots(Items.IRON_BOOTS)), new BootHalf(boots(Items.GOLDEN_BOOTS))));
        return mixed;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), 1);
    }

    private static ItemStack boots(Item item) {
        return equippable(item, EquipmentSlot.FEET);
    }

    private static ItemStack equippable(Item item, EquipmentSlot slot) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build())
                .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }
}
