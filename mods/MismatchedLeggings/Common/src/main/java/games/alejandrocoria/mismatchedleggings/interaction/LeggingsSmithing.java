package games.alejandrocoria.mismatchedleggings.interaction;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.mixin.IngredientAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.util.Optional;

public final class LeggingsSmithing {
    private LeggingsSmithing() {
    }

    public static Optional<Result> evaluate(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null || addition.isEmpty()) {
            return Optional.empty();
        }
        var left = source(level, template, data.left().source(), addition);
        var right = source(level, template, data.right().source(), addition);
        if (left.recipe() == null && right.recipe() == null) {
            return Optional.empty();
        }
        try {
            var output = MixedLeggingsRecomposer.recompose(root, left.stack(), right.stack());
            var primary = left.recipe() != null ? left.recipe() : right.recipe();
            var additional = left.recipe() != null && right.recipe() != null && !left.recipe().id().equals(right.recipe().id())
                    ? right.recipe() : null;
            return Optional.of(new Result(output, primary, additional));
        } catch (IllegalArgumentException exception) {
            LeggingsContent.LOG.debug("Rejected source-wise leggings smithing: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    /** Generic native trim admission is not a dedicated recipe for the mixed root. */
    public static boolean isGenericRootTrim(RecipeHolder<?> recipe) {
        return recipe != null && recipe.value().getClass() == SmithingTrimRecipe.class
                && ((IngredientAccessor) (Object) ((SmithingTrimRecipe) recipe.value()).baseIngredient()).mismatchedleggings$values()
                .unwrapKey().filter(ItemTags.TRIMMABLE_ARMOR::equals).isPresent();
    }

    public static boolean hasDirectResult(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var input = new SmithingRecipeInput(template.copy(), root.copy(), addition.copy());
        var match = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
        return match.isPresent() && !isGenericRootTrim(match.orElseThrow()) && !match.orElseThrow().value().assemble(input).isEmpty();
    }

    private static Side source(ServerLevel level, ItemStack template, ItemStack source, ItemStack addition) {
        var input = new SmithingRecipeInput(template.copy(), source.copy(), addition.copy());
        var match = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
        if (match.isPresent()) {
            var result = match.orElseThrow().value().assemble(input);
            if (LeggingHalf.isValidSource(result) && !ItemStack.matches(source, result)) {
                return new Side(result, match.orElseThrow());
            }
        }
        return new Side(source, null);
    }

    public record Result(ItemStack output, RecipeHolder<SmithingRecipe> primary, RecipeHolder<SmithingRecipe> additional) {
    }

    private record Side(ItemStack stack, RecipeHolder<SmithingRecipe> recipe) {
    }
}
