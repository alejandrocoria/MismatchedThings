package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

import java.util.Optional;

public final class MixedBootsSmithingPolicy {
    private MixedBootsSmithingPolicy() {
    }

    public static Optional<Result> evaluate(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        if (!ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(root.getItem()))
                || root.getCount() != 1 || addition.isEmpty()
                || ItemStack.validateStrict(root).error().isPresent()) {
            return Optional.empty();
        }
        Optional<MixedBootsData> data = ModContent.getMixedBootsData(root);
        if (data.isEmpty()) {
            return Optional.empty();
        }

        RootAttempt rootAttempt = Services.PLATFORM.tryRootSmithing(level, template, root, addition);
        if (rootAttempt.handled()) { return Optional.ofNullable(rootAttempt.result()); }

        // Template requirements belong to the loaded recipes, including recipes
        // that require the slot to be empty. Pass the actual input unchanged.
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
        if (!isValidChange(source, assembled)) {
            return new Side(source, null);
        }
        return new Side(assembled, match.get());
    }

    private static boolean isValidChange(ItemStack source, ItemStack assembled) {
        return BootHalf.isValidSource(assembled) && !ItemStack.matches(source, assembled);
    }

    public record Result(
            ItemStack output,
            RecipeHolder<SmithingRecipe> primaryRecipe,
            RecipeHolder<SmithingRecipe> additionalRecipe) {
    }

    private record Side(ItemStack source, RecipeHolder<SmithingRecipe> recipe) {
    }

    /** Null result means handled rejection, not permission to try another ownership model. */
    public record RootAttempt(boolean handled, Result result) {
        public static final RootAttempt UNHANDLED = new RootAttempt(false, null);
        public static final RootAttempt REJECTED = new RootAttempt(true, null);
    }
}
