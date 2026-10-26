package games.alejandrocoria.mismatchedboots.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsRepairPolicy;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

public final class MixedBootsRepairRecipe extends CustomRecipe {
    private static final MixedBootsRepairRecipe INSTANCE = new MixedBootsRepairRecipe();
    private static final MapCodec<MixedBootsRepairRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsRepairRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<MixedBootsRepairRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Predicate<ItemStack> combineRepairable;
    private final Predicate<Holder<Enchantment>> curse;

    public MixedBootsRepairRecipe() {
        this(MixedBootsRepairRecipe::platformAllowsRepair, enchantment -> enchantment.is(EnchantmentTags.CURSE));
    }

    MixedBootsRepairRecipe(
            Predicate<ItemStack> combineRepairable,
            Predicate<Holder<Enchantment>> curse) {
        this.combineRepairable = combineRepairable;
        this.curse = curse;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = findMatch(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = match.mixed().copyWithCount(1);
        MixedBootsRepairPolicy.restoreDurability(result, match.mixed(), match.donor());
        result.set(DataComponents.ENCHANTMENTS, collectCurses(match.mixed(), match.donor()));
        result.remove(DataComponents.CUSTOM_NAME);
        result.remove(DataComponents.REPAIR_COST);

        var error = ItemStack.validateStrict(result).error();
        if (error.isPresent()) {
            Constants.LOG.debug("Rejected crafting repair output for {} and {}: {}",
                                BuiltInRegistries.ITEM.getKey(match.mixed().getItem()),
                                BuiltInRegistries.ITEM.getKey(match.donor().getItem()),
                                error.get().message());
            return ItemStack.EMPTY;
        }

        return result;
    }

    @Override
    public RecipeSerializer<MixedBootsRepairRecipe> getSerializer() {
        return SERIALIZER;
    }

    private Match findMatch(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }

        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else if (second.isEmpty()) {
                second = stack;
            } else {
                return null;
            }
        }

        boolean firstMixed = ModContent.isMixedBoots(first);
        boolean secondMixed = ModContent.isMixedBoots(second);
        if (firstMixed == secondMixed) {
            return null;
        }

        ItemStack mixed = firstMixed ? first : second;
        ItemStack donor = firstMixed ? second : first;
        if (mixed.getCount() != 1 || donor.getCount() != 1
                || !hasDurabilityComponents(mixed) || !hasDurabilityComponents(donor)
                || !MixedBootsRepairPolicy.canUseOrdinaryDonor(mixed, donor)
                || !combineRepairable.test(mixed) || !combineRepairable.test(donor)) {
            return null;
        }

        return new Match(mixed, donor);
    }

    private ItemEnchantments collectCurses(ItemStack mixed, ItemStack donor) {
        ItemEnchantments.Mutable result = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addCurses(result, EnchantmentHelper.getEnchantmentsForCrafting(mixed));
        addCurses(result, EnchantmentHelper.getEnchantmentsForCrafting(donor));
        return result.toImmutable();
    }

    private void addCurses(ItemEnchantments.Mutable result, ItemEnchantments enchantments) {
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (curse.test(enchantment)) {
                result.set(enchantment, Math.max(result.getLevel(enchantment), enchantments.getLevel(enchantment)));
            }
        }
    }

    private static boolean hasDurabilityComponents(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) && stack.has(DataComponents.DAMAGE);
    }

    private static boolean platformAllowsRepair(ItemStack stack) {
        return Services.PLATFORM.canCombineRepair(stack);
    }

    private record Match(ItemStack mixed, ItemStack donor) {
    }
}
