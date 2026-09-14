package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;

public final class MixedBootsFactory {
    private MixedBootsFactory() {
    }

    public static MixedBootsPair createPair(ItemStack first, ItemStack second) {
        Holder<Item> mixedBoots = BuiltInRegistries.ITEM
                .get(ModContent.MIXED_BOOTS_ID)
                .filter(holder -> holder.value() instanceof MixedBootsItem)
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots item is not registered"));
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots data component is not registered"));
        return createPair(first, second, mixedBoots, dataType);
    }

    static MixedBootsPair createPair(
            ItemStack first,
            ItemStack second,
            Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType) {
        return createPair(first, second, mixedBoots, dataType, AttributeRuleState.current());
    }

    static MixedBootsPair createPair(
            ItemStack first,
            ItemStack second,
            Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType,
            AttributeRuleSet rules) {
        BootHalf firstHalf = new BootHalf(first);
        BootHalf secondHalf = new BootHalf(second);
        ItemStack firstSource = firstHalf.source();
        ItemStack secondSource = secondHalf.source();
        DurabilityMerger.Result durability = DurabilityMerger.merge(firstSource, secondSource);
        AttributeComposer.Result attributes = AttributeComposer.compose(firstSource, secondSource, rules);
        ItemEnchantments enchantments = EnchantmentMerger.merge(firstSource, secondSource);

        ItemStack primary = createOrientation(
                firstHalf, secondHalf, mixedBoots, dataType, durability, attributes.primary(), enchantments);
        ItemStack inverse = createOrientation(
                secondHalf, firstHalf, mixedBoots, dataType, durability, attributes.inverse(), enchantments);
        return new MixedBootsPair(primary, inverse);
    }

    private static ItemStack createOrientation(
            BootHalf left,
            BootHalf right,
            Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType,
            DurabilityMerger.Result durability,
            ItemAttributeModifiers attributes,
            ItemEnchantments enchantments) {
        ItemStack owner = left.source();
        ItemStack other = right.source();
        ItemStack output = new ItemStack(mixedBoots, 1);

        List.copyOf(output.getComponents().keySet()).forEach(output::remove);
        output.applyComponents(owner.getComponents());

        output.remove(DataComponents.REPAIRABLE);
        output.remove(dataType);

        output.set(DataComponents.MAX_STACK_SIZE, 1);
        output.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"));
        output.set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID);
        output.set(DataComponents.EQUIPPABLE, projectEquippable(owner));

        output.remove(DataComponents.MAX_DAMAGE);
        output.remove(DataComponents.DAMAGE);
        if (durability.damageable()) {
            output.set(DataComponents.MAX_DAMAGE, durability.maximum());
            output.set(DataComponents.DAMAGE, durability.damage());
        }

        output.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        output.set(DataComponents.ENCHANTMENTS, enchantments);
        output.set(dataType, new MixedBootsData(left, right));

        Optional<com.mojang.serialization.DataResult.Error<ItemStack>> error = ItemStack.validateStrict(output).error();
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid mixed boots output: " + error.get().message());
        }
        return output;
    }

    private static Equippable projectEquippable(ItemStack owner) {
        Equippable source = owner.get(DataComponents.EQUIPPABLE);
        if (source == null) {
            throw new IllegalArgumentException("Mixed boots owner is not feet-equippable");
        }
        return new Equippable(
                EquipmentSlot.FEET,
                source.equipSound(),
                source.assetId(),
                Optional.empty(),
                source.allowedEntities(),
                source.dispensable(),
                source.swappable(),
                source.damageOnHurt(),
                source.equipOnInteract(),
                source.canBeSheared(),
                source.shearingSound());
    }
}
