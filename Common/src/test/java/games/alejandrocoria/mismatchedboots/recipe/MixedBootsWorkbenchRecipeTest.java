package games.alejandrocoria.mismatchedboots.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsWorkbenchRecipeTest {
    private static DataComponentType<MixedBootsData> mixedDataType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
    }

    @Test
    void inputHasExactlyTwoOrderedSlots() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(left, right);

        assertEquals(2, input.size());
        assertSame(left, input.getItem(0));
        assertSame(right, input.getItem(1));
        assertThrows(IndexOutOfBoundsException.class, () -> input.getItem(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> input.getItem(2));
        assertThrows(NullPointerException.class, () -> new MismatchedWorkbenchInput(null, right));
        assertThrows(NullPointerException.class, () -> new MismatchedWorkbenchInput(left, null));
    }

    @Test
    void assemblesOneOrderedPairWithRepresentativeSourceComponents() {
        ItemStack left = boots(Items.IRON_BOOTS);
        left.set(DataComponents.MAX_DAMAGE, 200);
        left.set(DataComponents.DAMAGE, 37);
        left.set(DataComponents.CUSTOM_NAME, Component.literal("Left source"));
        left.set(DataComponents.ENCHANTMENTS, enchantments());
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        right.set(DataComponents.TRIM, trim());
        right.set(DataComponents.ENCHANTABLE, new Enchantable(23));
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ItemStack> receivedLeft = new AtomicReference<>();
        AtomicReference<ItemStack> receivedRight = new AtomicReference<>();
        MixedBootsWorkbenchRecipe recipe = new MixedBootsWorkbenchRecipe((first, second) -> {
            calls.incrementAndGet();
            receivedLeft.set(first);
            receivedRight.set(second);
            return new MixedBootsPair(first.copy(), second.copy());
        });
        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(left, right);

        assertTrue(recipe.matches(input, null));
        MismatchedWorkbenchResult result = recipe.assemblePair(input);

        assertEquals(1, calls.get());
        assertTrue(result.isComplete());
        assertTrue(ItemStack.matches(leftBefore, result.primary()));
        assertTrue(ItemStack.matches(rightBefore, result.inverse()));
        assertTrue(ItemStack.matches(leftBefore, receivedLeft.get()));
        assertTrue(ItemStack.matches(rightBefore, receivedRight.get()));
        assertNotSame(left, receivedLeft.get());
        assertNotSame(right, receivedRight.get());
        assertTrue(ItemStack.matches(leftBefore, left));
        assertTrue(ItemStack.matches(rightBefore, right));
        assertTrue(ItemStack.matches(result.primary(), recipe.assemble(input)));
    }

    @Test
    void acceptsSameBaseItemWithDistinctComponents() {
        ItemStack left = boots(Items.IRON_BOOTS);
        left.set(DataComponents.CUSTOM_NAME, Component.literal("Left source"));
        ItemStack right = boots(Items.IRON_BOOTS);
        right.set(DataComponents.CUSTOM_NAME, Component.literal("Right source"));
        MixedBootsWorkbenchRecipe recipe = copyingRecipe();

        MismatchedWorkbenchResult result = recipe.assemblePair(
                new MismatchedWorkbenchInput(left, right));

        assertTrue(result.isComplete());
        assertEquals(Items.IRON_BOOTS, result.primary().getItem());
        assertEquals(Items.IRON_BOOTS, result.inverse().getItem());
        assertEquals(Component.literal("Left source"), result.primary().get(DataComponents.CUSTOM_NAME));
        assertEquals(Component.literal("Right source"), result.inverse().get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void resultOwnsBothOutputsAndReturnsDefensiveCopies() {
        ItemStack primary = boots(Items.IRON_BOOTS);
        ItemStack inverse = boots(Items.GOLDEN_BOOTS);
        MismatchedWorkbenchResult result = new MismatchedWorkbenchResult(primary, inverse);

        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated source"));
        inverse.setCount(0);
        ItemStack exposedPrimary = result.primary();
        exposedPrimary.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated accessor"));

        assertNull(result.primary().get(DataComponents.CUSTOM_NAME));
        assertEquals(1, result.inverse().getCount());
        assertTrue(result.isComplete());
        assertFalse(MismatchedWorkbenchResult.EMPTY.isComplete());
    }

    @Test
    void rejectsEmptyWrongSlotWrongCountMalformedAndRecursiveSources() {
        MixedBootsWorkbenchRecipe recipe = copyingRecipe();
        ItemStack valid = boots(Items.IRON_BOOTS);
        ItemStack helmet = boots(Items.IRON_HELMET);
        helmet.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build());
        ItemStack stacked = boots(Items.GOLDEN_BOOTS);
        stacked.setCount(2);
        ItemStack malformed = boots(Items.DIAMOND_BOOTS);
        malformed.set(DataComponents.MAX_STACK_SIZE, 2);
        malformed.set(DataComponents.MAX_DAMAGE, 10);
        ItemStack recursive = boots(Items.DIAMOND_BOOTS);
        recursive.set(mixedDataType, new MixedBootsData(
                new BootHalf(boots(Items.IRON_BOOTS)),
                new BootHalf(boots(Items.GOLDEN_BOOTS))));

        for (ItemStack invalid : List.of(ItemStack.EMPTY, helmet, stacked, malformed, recursive)) {
            MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(valid, invalid);
            assertFalse(recipe.matches(input, null));
            assertFalse(recipe.assemblePair(input).isComplete());
            assertTrue(recipe.assemble(input).isEmpty());
        }
    }

    @Test
    void compositionFailureOrIncompletePairExposesNoOutput() {
        AtomicInteger failures = new AtomicInteger();
        MixedBootsWorkbenchRecipe failing = new MixedBootsWorkbenchRecipe((left, right) -> {
            failures.incrementAndGet();
            left.set(DataComponents.CUSTOM_NAME, Component.literal("Factory mutation"));
            throw new IllegalArgumentException("unsupported source component");
        });
        MixedBootsWorkbenchRecipe incomplete = new MixedBootsWorkbenchRecipe(
                (left, right) -> new MixedBootsPair(left, ItemStack.EMPTY));
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack leftBefore = left.copy();
        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(left, boots(Items.GOLDEN_BOOTS));

        assertFalse(failing.assemblePair(input).isComplete());
        assertEquals(1, failures.get());
        assertTrue(ItemStack.matches(leftBefore, left));
        assertFalse(incomplete.assemblePair(input).isComplete());
    }

    @Test
    void exposesSpecialNonPlaceableSharedRecipeContract() {
        MixedBootsWorkbenchRecipe recipe = copyingRecipe();
        AlternateWorkbenchRecipe alternate = AlternateWorkbenchRecipe.INSTANCE;

        assertSame(MismatchedWorkbenchRecipe.TYPE, recipe.getType());
        assertSame(recipe.getType(), alternate.getType());
        assertSame(recipe.getType(), ModContent.mismatchedWorkbenchRecipeType());
        assertSame(MixedBootsWorkbenchRecipe.SERIALIZER, recipe.getSerializer());
        assertSame(MixedBootsWorkbenchRecipe.SERIALIZER,
                   ModContent.createMixedBootsWorkbenchRecipeSerializer());
        assertNotSame(recipe.getSerializer(), alternate.getSerializer());
        assertEquals(ModContent.MISMATCHED_WORKBENCH_ID.toString(), recipe.getType().toString());
        assertTrue(recipe.isSpecial());
        assertFalse(recipe.showNotification());
        assertTrue(recipe.group().isEmpty());
        assertSame(PlacementInfo.NOT_PLACEABLE, recipe.placementInfo());
        assertTrue(recipe.display().isEmpty());
    }

    @Test
    void statelessSerializerRoundTripsPersistentAndNetworkForms() {
        RecipeSerializer<MixedBootsWorkbenchRecipe> serializer =
                ModContent.createMixedBootsWorkbenchRecipeSerializer();
        JsonElement encoded = serializer.codec().codec()
                                        .encodeStart(JsonOps.INSTANCE, new MixedBootsWorkbenchRecipe()).getOrThrow();
        MixedBootsWorkbenchRecipe persistent = serializer.codec().codec()
                                                         .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), builtInRegistryAccess());

        try {
            serializer.streamCodec().encode(buffer, persistent);
            MixedBootsWorkbenchRecipe network = serializer.streamCodec().decode(buffer);

            assertTrue(encoded.isJsonObject());
            assertSame(serializer, persistent.getSerializer());
            assertSame(serializer, network.getSerializer());
            assertSame(MismatchedWorkbenchRecipe.TYPE, persistent.getType());
            assertSame(MismatchedWorkbenchRecipe.TYPE, network.getType());
        } finally {
            buffer.release();
        }
    }

    private static MixedBootsWorkbenchRecipe copyingRecipe() {
        return new MixedBootsWorkbenchRecipe(
                (left, right) -> new MixedBootsPair(left.copy(), right.copy()));
    }

    private static ItemStack boots(Item item) {
        DataComponentMap components = DataComponentMap.builder()
                                                      .set(DataComponents.MAX_STACK_SIZE, 1)
                                                      .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                                                      .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                                                      .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }

    private static ItemEnchantments enchantments() {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(Holder.direct(Items.IRON_BOOTS)),
                1,
                3,
                Enchantment.constantCost(1),
                Enchantment.constantCost(1),
                1,
                EquipmentSlotGroup.FEET);
        Holder<Enchantment> enchantment = Holder.direct(new Enchantment(
                Component.literal("Test enchantment"),
                definition,
                HolderSet.direct(List.of()),
                DataComponentMap.EMPTY));
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, 2);
        return mutable.toImmutable();
    }

    private static ArmorTrim trim() {
        Holder<TrimMaterial> material = Holder.direct(new TrimMaterial(
                MaterialAssetGroup.QUARTZ, Component.literal("Quartz")));
        Holder<TrimPattern> pattern = Holder.direct(new TrimPattern(
                Identifier.fromNamespaceAndPath("test", "pattern"),
                Component.literal("Pattern"),
                false));
        return new ArmorTrim(material, pattern);
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }

    private static final class AlternateWorkbenchRecipe implements MismatchedWorkbenchRecipe {
        private static final AlternateWorkbenchRecipe INSTANCE = new AlternateWorkbenchRecipe();
        private static final RecipeSerializer<AlternateWorkbenchRecipe> SERIALIZER = new RecipeSerializer<>(
                MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

        @Override
        public boolean matches(MismatchedWorkbenchInput input, net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public MismatchedWorkbenchResult assemblePair(MismatchedWorkbenchInput input) {
            return MismatchedWorkbenchResult.EMPTY;
        }

        @Override
        public RecipeSerializer<AlternateWorkbenchRecipe> getSerializer() {
            return SERIALIZER;
        }
    }
}
