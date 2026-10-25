package games.alejandrocoria.mismatchedboots.composition;

import com.mojang.serialization.Lifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MixedBootsGrindstoneXpPolicyTest {
    private static DataComponentType<MixedBootsData> mixedDataType;
    private static Holder<Enchantment> nonlinear;
    private static Holder<Enchantment> secondary;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
        MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        nonlinear = enchantment(registry, "nonlinear", Enchantment.dynamicCost(1, 11));
        secondary = enchantment(registry, "secondary", Enchantment.constantCost(4));
        registry.freeze();
    }

    @Test
    void capsNonlinearMergedLevelsByOriginalSources() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        enchant(left, nonlinear, 1);
        enchant(right, nonlinear, 1);
        ItemStack mixed = mixed(left, right);
        enchant(mixed, nonlinear, 2);

        assertEquals(12, nonlinear.value().getMinCost(2));
        assertEquals(1, MixedBootsGrindstoneXpPolicy.adjustPotential(mixed, 12));
    }

    @Test
    void discountsOneSidedAndDistinctSourceEnchantments() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        enchant(left, secondary, 1);
        ItemStack oneSided = mixed(left, right);
        enchant(oneSided, secondary, 1);
        assertEquals(2, MixedBootsGrindstoneXpPolicy.adjustPotential(oneSided, 4));

        enchant(right, nonlinear, 1);
        ItemStack distinct = mixed(left, right);
        enchant(distinct, secondary, 1);
        addEnchantment(distinct, nonlinear, 1);
        assertEquals(2, MixedBootsGrindstoneXpPolicy.adjustPotential(distinct, 5));
    }

    @Test
    void countsLaterRootIncreaseButNotStrippedBaseline() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        enchant(left, nonlinear, 1);
        enchant(right, nonlinear, 1);
        ItemStack mixed = mixed(left, right);
        enchant(mixed, nonlinear, 2);
        addEnchantment(mixed, secondary, 1);
        assertEquals(5, MixedBootsGrindstoneXpPolicy.adjustPotential(mixed, 16));

        mixed.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        assertEquals(0, MixedBootsGrindstoneXpPolicy.adjustPotential(mixed, 0));
    }

    @Test
    void leavesOrdinaryStacksAloneAndClampsMalformedPotential() {
        assertEquals(17, MixedBootsGrindstoneXpPolicy.adjustPotential(boots(Items.IRON_BOOTS), 17));
        assertEquals(0, MixedBootsGrindstoneXpPolicy.boundedPotential(-10, 12, 2));
        assertEquals(0, MixedBootsGrindstoneXpPolicy.boundedPotential(12, -4, -2));
        assertEquals(1, MixedBootsGrindstoneXpPolicy.boundedPotential(12, 12, 2));
        assertEquals(Integer.MAX_VALUE, MixedBootsGrindstoneXpPolicy.boundedPotential(
                Integer.MAX_VALUE, 0, Long.MAX_VALUE));
    }

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.builder()
                                                                 .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                                                                 .build()));
    }

    private static ItemStack mixed(ItemStack left, ItemStack right) {
        ItemStack stack = boots(Items.CHAINMAIL_BOOTS);
        stack.set(mixedDataType, new MixedBootsData(new BootHalf(left), new BootHalf(right)));
        return stack;
    }

    private static void enchant(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        values.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
    }

    private static void addEnchantment(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(stack.getOrDefault(
                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        values.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
    }

    private static Holder<Enchantment> enchantment(
            MappedRegistry<Enchantment> registry, String name, Enchantment.Cost cost) {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(Holder.direct(Items.IRON_BOOTS)), 1, 5,
                cost, Enchantment.constantCost(1), 1, EquipmentSlotGroup.FEET);
        return registry.register(
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("test", name)),
                new Enchantment(Component.literal(name), definition, HolderSet.empty(), DataComponentMap.EMPTY),
                RegistrationInfo.BUILT_IN);
    }
}
