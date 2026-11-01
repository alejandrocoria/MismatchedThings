package games.alejandrocoria.mismatchedleggings.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsRepair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/** One mixed root plus one ordinary source donor, never a fresh recomposition. */
public final class MixedLeggingsRepairRecipe extends CustomRecipe {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "mixed_leggings_repair");
    private final Predicate<ItemStack> combineRepairable;
    private final RecipeSerializer<MixedLeggingsRepairRecipe> serializer;

    public MixedLeggingsRepairRecipe(Predicate<ItemStack> combineRepairable) {
        this.combineRepairable = combineRepairable;
        // Each loader supplies its native eligibility hook without a cross-product platform service.
        serializer = new RecipeSerializer<>(MapCodec.unit(this),
                StreamCodec.<RegistryFriendlyByteBuf, MixedLeggingsRepairRecipe>unit(this));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        var match = findMatch(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }

        var result = match.mixed().copyWithCount(1);
        result.setDamageValue(LeggingsRepair.craftingDamage(match.mixed(), match.donor()));
        var curses = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addCurses(curses, match.mixed());
        addCurses(curses, match.donor());
        result.set(DataComponents.ENCHANTMENTS, curses.toImmutable());
        result.remove(DataComponents.CUSTOM_NAME);
        result.remove(DataComponents.REPAIR_COST);
        return ItemStack.validateStrict(result).isError() ? ItemStack.EMPTY : result;
    }

    @Override
    public RecipeSerializer<MixedLeggingsRepairRecipe> getSerializer() {
        return serializer;
    }

    private Match findMatch(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }

        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        for (var stack : input.items()) {
            if (!stack.isEmpty()) {
                if (first.isEmpty()) {
                    first = stack;
                } else {
                    second = stack;
                }
            }
        }

        boolean firstMixed = MixedLeggingsStructure.isMixedItem(first);
        if (firstMixed == MixedLeggingsStructure.isMixedItem(second)) {
            return null;
        }
        var mixed = firstMixed ? first : second;
        var donor = firstMixed ? second : first;
        if (!LeggingsRepair.acceptsDonor(mixed, donor)
                || !donor.has(DataComponents.MAX_DAMAGE) || !donor.has(DataComponents.DAMAGE)
                || !combineRepairable.test(mixed) || !combineRepairable.test(donor)) {
            return null;
        }
        return new Match(mixed, donor);
    }

    private static void addCurses(ItemEnchantments.Mutable result, ItemStack stack) {
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var enchantment : enchantments.keySet()) {
            if (enchantment.is(EnchantmentTags.CURSE)) {
                result.set(enchantment, Math.max(result.getLevel(enchantment), enchantments.getLevel(enchantment)));
            }
        }
    }

    private record Match(ItemStack mixed, ItemStack donor) {
    }
}
