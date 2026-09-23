package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

import java.util.Objects;
import java.util.Optional;

public final class MixedBootsSmithingPolicy {
    private MixedBootsSmithingPolicy() {
    }

    public static Optional<Result> evaluate(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        if (!ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(root.getItem()))
                || root.getCount() != 1 || template.isEmpty() || addition.isEmpty()
                || ItemStack.validateStrict(root).error().isPresent()) {
            return Optional.empty();
        }
        Optional<MixedBootsData> data = ModContent.getMixedBootsData(root);
        if (data.isEmpty()) {
            return Optional.empty();
        }

        Side left = evaluateSource(level, template, data.get().left().source(), addition);
        Side right = evaluateSource(level, template, data.get().right().source(), addition);
        if (left.recipe() == null && right.recipe() == null) {
            return Optional.empty();
        }

        try {
            ItemStack result = MixedBootsRecomposer.recompose(root, left.source(), right.source());
            RecipeHolder<SmithingRecipe> primary = left.recipe() != null ? left.recipe() : right.recipe();
            RecipeHolder<SmithingRecipe> additional = left.recipe() != null && right.recipe() != null
                    && !left.recipe().id().equals(right.recipe().id()) ? right.recipe() : null;
            return Optional.of(new Result(result, primary, additional));
        } catch (IllegalArgumentException exception) {
            Constants.LOG.debug("Rejected mixed boots smithing recomposition: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static Side evaluateSource(
            ServerLevel level, ItemStack template, ItemStack source, ItemStack addition) {
        SmithingRecipeInput input = new SmithingRecipeInput(template.copy(), source.copy(), addition.copy());
        Optional<RecipeHolder<SmithingRecipe>> match = level.recipeAccess()
                .getRecipeFor(RecipeType.SMITHING, input, level);
        if (match.isEmpty()) {
            return new Side(source, null);
        }

        ItemStack assembled = match.get().value().assemble(input);
        if (!isTrimLikeChange(source, assembled)) {
            return new Side(source, null);
        }
        return new Side(assembled, match.get());
    }

    private static boolean isTrimLikeChange(ItemStack source, ItemStack assembled) {
        if (!BootHalf.isValidSource(assembled) || !assembled.is(source.getItem())
                || Objects.equals(source.get(DataComponents.TRIM), assembled.get(DataComponents.TRIM))) {
            return false;
        }
        ItemStack oldWithoutTrim = source.copy();
        ItemStack newWithoutTrim = assembled.copy();
        oldWithoutTrim.remove(DataComponents.TRIM);
        newWithoutTrim.remove(DataComponents.TRIM);
        return ItemStack.matches(oldWithoutTrim, newWithoutTrim);
    }

    public record Result(
            ItemStack output,
            RecipeHolder<SmithingRecipe> primaryRecipe,
            RecipeHolder<SmithingRecipe> additionalRecipe) {
    }

    private record Side(ItemStack source, RecipeHolder<SmithingRecipe> recipe) {
    }
}
