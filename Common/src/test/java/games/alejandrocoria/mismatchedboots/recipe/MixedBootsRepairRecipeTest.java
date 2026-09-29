package games.alejandrocoria.mismatchedboots.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsRepairRecipeTest {
    private static DataComponentType<MixedBootsData> mixedDataType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
    }

    @Test
    void acceptsEitherSourceInEitherOrderAndSparseLayouts() {
        MixedBootsRepairRecipe recipe = recipe(stack -> true, enchantment -> false);
        ItemStack mixed = mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack iron = boots(Items.IRON_BOOTS, 100);
        ItemStack gold = boots(Items.GOLDEN_BOOTS, 100);

        assertMatches(recipe, input(2, 2, 0, mixed, 3, iron));
        assertMatches(recipe, input(2, 2, 0, iron, 3, mixed));
        assertMatches(recipe, input(3, 3, 1, mixed, 8, gold));
        assertMatches(recipe, input(3, 3, 1, gold, 8, mixed));
    }

    @Test
    void rejectsUnsupportedInputShapesAndMalformedStacks() {
        MixedBootsRepairRecipe recipe = recipe(stack -> true, enchantment -> false);
        ItemStack mixed = mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack donor = boots(Items.IRON_BOOTS, 100);
        ItemStack stacked = donor.copy();
        stacked.setCount(2);
        ItemStack stackedMixed = mixed.copy();
        stackedMixed.setCount(2);
        ItemStack missingDamage = donor.copy();
        missingDamage.remove(DataComponents.DAMAGE);
        ItemStack missingMaximum = donor.copy();
        missingMaximum.remove(DataComponents.MAX_DAMAGE);
        ItemStack malformedMixed = mixed.copy();
        malformedMixed.remove(DataComponents.DAMAGE);

        List<CraftingInput> rejected = List.of(
                input(2, 2, 0, mixed),
                input(2, 2, 0, mixed, 1, boots(Items.DIAMOND_BOOTS, 100)),
                input(2, 2, 0, mixed, 1, mixed.copy()),
                input(2, 2, 0, malformedMixed, 1, donor),
                input(2, 2, 0, mixed, 1, stacked),
                input(2, 2, 0, stackedMixed, 1, donor),
                input(2, 2, 0, mixed, 1, missingDamage),
                input(2, 2, 0, mixed, 1, missingMaximum),
                input(2, 2, 0, mixed, 1, donor, 2, boots(Items.GOLDEN_BOOTS, 100)));

        for (CraftingInput input : rejected) {
            assertFalse(recipe.matches(input, null));
            assertTrue(recipe.assemble(input).isEmpty());
        }
    }

    @Test
    void respectsThePlatformHookForBothInputs() {
        ItemStack mixed = mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack donor = boots(Items.IRON_BOOTS, 100);
        CraftingInput input = input(2, 2, 0, mixed, 1, donor);
        AtomicInteger calls = new AtomicInteger();
        MixedBootsRepairRecipe accepts = recipe(stack -> {
            calls.incrementAndGet();
            return true;
        }, enchantment -> false);

        assertTrue(accepts.matches(input, null));
        assertEquals(2, calls.get());
        assertFalse(recipe(stack -> stack != mixed, enchantment -> false).matches(input, null));
        assertFalse(recipe(stack -> stack != donor, enchantment -> false).matches(input, null));
    }

    @Test
    void assemblesVanillaStyleRepairWithoutLosingMixedState() {
        Holder<Enchantment> curse = enchantment("test_curse");
        Holder<Enchantment> ordinary = enchantment("test_enchantment");
        MixedBootsRepairRecipe recipe = recipe(stack -> true, curse::equals);
        ItemStack mixed = mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.set(DataComponents.MAX_DAMAGE, 200);
        mixed.setDamageValue(150);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Named pair"));
        mixed.set(DataComponents.REPAIR_COST, 7);
        mixed.set(DataComponents.CUSTOM_DATA, customData("preserved"));
        mixed.set(DataComponents.ENCHANTMENTS, enchantments(curse, 1, ordinary, 3));
        ItemStack donor = boots(Items.IRON_BOOTS, 500);
        donor.setDamageValue(490);
        donor.set(DataComponents.ENCHANTMENTS, enchantments(curse, 2, ordinary, 1));
        ItemStack mixedBefore = mixed.copy();
        ItemStack donorBefore = donor.copy();
        MixedBootsData dataBefore = ModContent.getMixedBootsData(mixed).orElseThrow();

        ItemStack result = recipe.assemble(input(2, 2, 0, mixed, 3, donor));

        assertFalse(result.isEmpty());
        assertNotSame(mixed, result);
        assertSame(mixed.getItem(), result.getItem());
        assertEquals(200, result.getMaxDamage());
        assertEquals(130, result.getDamageValue());
        assertEquals(2, result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(curse));
        assertEquals(0, result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(ordinary));
        assertFalse(result.has(DataComponents.CUSTOM_NAME));
        assertFalse(result.has(DataComponents.REPAIR_COST));
        assertEquals(customData("preserved"), result.get(DataComponents.CUSTOM_DATA));
        assertEquals(dataBefore, ModContent.getMixedBootsData(result).orElseThrow());
        assertTrue(ItemStack.matches(mixedBefore, mixed));
        assertTrue(ItemStack.matches(donorBefore, donor));
    }

    @Test
    void clampsCompleteAndExhaustedRepairsWithoutMutatingInputs() {
        MixedBootsRepairRecipe recipe = recipe(stack -> true, enchantment -> false);
        ItemStack mixed = mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.set(DataComponents.MAX_DAMAGE, 200);
        ItemStack donor = boots(Items.IRON_BOOTS, 500);

        mixed.setDamageValue(150);
        donor.setDamageValue(0);
        assertEquals(0, recipe.assemble(input(2, 2, 0, mixed, 1, donor)).getDamageValue());

        mixed.setDamageValue(200);
        donor.setDamageValue(500);
        assertEquals(190, recipe.assemble(input(2, 2, 0, mixed, 1, donor)).getDamageValue());
        assertEquals(200, mixed.getDamageValue());
        assertEquals(500, donor.getDamageValue());
    }

    @Test
    @SuppressWarnings("deprecation")
    void exposesStatelessSpecialRecipeContractAndSerializerRoundTrip() {
        MixedBootsRepairRecipe recipe = recipe(stack -> true, enchantment -> false);
        RecipeSerializer<MixedBootsRepairRecipe> serializer = MixedBootsRepairRecipe.SERIALIZER;
        JsonElement encoded = serializer.codec().codec()
                .encodeStart(JsonOps.INSTANCE, new MixedBootsRepairRecipe()).getOrThrow();
        MixedBootsRepairRecipe persistent = serializer.codec().codec()
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), builtInRegistryAccess());

        try {
            serializer.streamCodec().encode(buffer, persistent);
            MixedBootsRepairRecipe network = serializer.streamCodec().decode(buffer);

            assertSame(RecipeType.CRAFTING, recipe.getType());
            assertSame(serializer, recipe.getSerializer());
            assertSame(serializer, ModContent.createMixedBootsRepairRecipeSerializer());
            assertEquals("mismatchedboots:mixed_boots_repair", ModContent.MIXED_BOOTS_REPAIR_RECIPE_ID.toString());
            assertSame(serializer, persistent.getSerializer());
            assertSame(serializer, network.getSerializer());
            assertTrue(encoded.isJsonObject());
            assertTrue(recipe.isSpecial());
            assertFalse(recipe.showNotification());
            assertTrue(recipe.group().isEmpty());
            assertSame(PlacementInfo.NOT_PLACEABLE, recipe.placementInfo());
            assertTrue(recipe.display().isEmpty());
        } finally {
            buffer.release();
        }
    }

    private static void assertMatches(MixedBootsRepairRecipe recipe, CraftingInput input) {
        assertTrue(recipe.matches(input, null));
        assertFalse(recipe.assemble(input).isEmpty());
    }

    private static MixedBootsRepairRecipe recipe(
            Predicate<ItemStack> combineRepairable,
            Predicate<Holder<Enchantment>> curse) {
        return new MixedBootsRepairRecipe(combineRepairable, curse);
    }

    private static ItemStack mixed(Item leftItem, Item rightItem) {
        ItemStack mixed = stack(Items.CHAINMAIL_BOOTS, 200);
        mixed.set(mixedDataType, new MixedBootsData(
                new BootHalf(boots(leftItem, 100)),
                new BootHalf(boots(rightItem, 100))));
        return mixed;
    }

    private static ItemStack boots(Item item, int maximumDamage) {
        return stack(item, maximumDamage);
    }

    private static ItemStack stack(Item item, int maximumDamage) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.MAX_DAMAGE, maximumDamage)
                .set(DataComponents.DAMAGE, 0)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
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
                Component.literal(name), definition, HolderSet.direct(List.of()), DataComponentMap.EMPTY));
    }

    private static ItemEnchantments enchantments(
            Holder<Enchantment> first,
            int firstLevel,
            Holder<Enchantment> second,
            int secondLevel) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(first, firstLevel);
        enchantments.set(second, secondLevel);
        return enchantments.toImmutable();
    }

    private static CustomData customData(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("value", value);
        return CustomData.of(tag);
    }

    private static CraftingInput input(int width, int height, Object... slotAndStacks) {
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(width * height, ItemStack.EMPTY));
        for (int index = 0; index < slotAndStacks.length; index += 2) {
            stacks.set((int) slotAndStacks[index], (ItemStack) slotAndStacks[index + 1]);
        }
        return CraftingInput.of(width, height, stacks);
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }
}
